package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.uml2.uml.Model;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.palladiosimulator.pcm.PcmPackage;
import tools.vitruv.applications.pcmumlclass.CombinedPcmToUmlClassReactionsChangePropagationSpecification;
import tools.vitruv.applications.pcmumlclass.CombinedUmlClassToPcmReactionsChangePropagationSpecification;
import tools.vitruv.applications.umljava.JavaToUmlChangePropagationSpecification;
import tools.vitruv.applications.umljava.UmlToJavaChangePropagationSpecification;
import tools.vitruv.applications.util.temporary.java.JavaSetup;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;
import tools.vitruv.dsls.reactions.migration.spec.SpecificationSource;
import tools.vitruv.dsls.reactions.migration.strategy.ExplicitDominance;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.testutils.integration.TestViewFactory;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

@ExtendWith(RegisterMetamodelsInStandalone.class)
class PcmEndToEndMigrationTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();
  private static final String UML_NS = "http://www.eclipse.org/uml2/5.0.0/UML";
  private static final String JAVA_NS_PREFIX = "http://www.emftext.org/java";
  private static final String PCM_NS_PREFIX = "http://palladiosimulator.org/PalladioComponentModel";

  private static final SpecificationSource UML_JAVA_PCM =
      () ->
          List.of(
              new UmlToJavaChangePropagationSpecification(),
              new JavaToUmlChangePropagationSpecification(),
              new CombinedUmlClassToPcmReactionsChangePropagationSpecification(),
              new CombinedPcmToUmlClassReactionsChangePropagationSpecification());

  @TempDir Path tempDir;

  @BeforeAll
  static void prepareStandalone() {
    Vsums.prepareStandalone(ADAPTERS);
    registerRecursively(PcmPackage.eINSTANCE);
    var factories = Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap();
    for (String extension : List.of("repository", "system", "resourcetype", "allocation", "pcm")) {
      factories.putIfAbsent(extension, new XMIResourceFactoryImpl());
    }
  }

  private static void registerRecursively(EPackage pkg) {
    EPackage.Registry.INSTANCE.putIfAbsent(pkg.getNsURI(), pkg);
    pkg.getESubpackages().forEach(PcmEndToEndMigrationTest::registerRecursively);
  }

  private static Set<String> nsUrisOf(List<EObject> roots) {
    return roots.stream()
        .map(root -> root.eClass().getEPackage().getNsURI())
        .collect(Collectors.toUnmodifiableSet());
  }

  private static Set<String> nsUrisOnDisk(Path folder) throws IOException {
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet.getLoadOptions().putAll(ADAPTERS.combinedLoadOptions());
    Set<String> nsUris = new TreeSet<>();
    List<Path> files;
    try (var walk = Files.walk(folder)) {
      files =
          walk.filter(Files::isRegularFile)
              .filter(
                  path -> {
                    String name = path.getFileName().toString();
                    return name.endsWith(".uml")
                        || name.endsWith(".java")
                        || name.endsWith(".repository");
                  })
              .filter(path -> ModelStructure.isOutsideJdkStandardLibrary(folder, path))
              .toList();
    }

    for (Path file : files) {
      Resource resource =
          resourceSet.getResource(URI.createFileURI(file.toAbsolutePath().toString()), true);
      assertTrue(resource.getErrors().isEmpty(), "no load errors in " + file);
      resource.getContents().forEach(root -> nsUris.add(root.eClass().getEPackage().getNsURI()));
    }

    return nsUris;
  }


  private static TestUserInteraction permissive() {
    TestUserInteraction interaction = new TestUserInteraction();
    interaction.onTextInput(d -> true).always().respondWith("library");
    interaction.onMultipleChoiceSingleSelection(d -> true).always().respondWithChoiceAt(0);
    interaction.onConfirmation(d -> true).always().respondWith(true);
    interaction.acknowledgeNotification(d -> true);
    return interaction;
  }

  @Test
  @DisplayName("migrates the library example across UML, Java, and PCM together")
  void test1() throws Exception {
    JavaSetup.resetClasspathAndRegisterStandardLibrary();
    Path sourceFolder = Files.createDirectories(tempDir.resolve("pcm-vsum"));
    Set<String> seededNsUris = seedThreeMetamodelVsum(sourceFolder);

    assertTrue(seededNsUris.contains(UML_NS), "UML seeded, got " + seededNsUris);
    assertTrue(
        seededNsUris.stream().anyMatch(u -> u.startsWith(JAVA_NS_PREFIX)),
        "Java derived, got " + seededNsUris);
    assertTrue(
        seededNsUris.stream().anyMatch(u -> u.startsWith(PCM_NS_PREFIX)),
        "PCM derived, got " + seededNsUris);

    MigrationReport report =
        new MigrationRunner(
                UML_JAVA_PCM,
                new PropagationGraph(UML_JAVA_PCM.createSpecifications()),
                new ExplicitDominance("uml"),
                new TestUserInteraction.ResultProvider(permissive()),
                ADAPTERS,
                MigrationSettings.defaults())
            .run(sourceFolder);

    assertTrue(report.migrated());
    assertTrue(report.sourceUpdate().attempted());
    Set<String> migratedNsUris = nsUrisOnDisk(sourceFolder);
    assertFalse(migratedNsUris.isEmpty(), "the migration must persist models");
    assertTrue(migratedNsUris.contains(UML_NS), "UML must survive, got " + migratedNsUris);
    assertTrue(
        migratedNsUris.stream().anyMatch(u -> u.startsWith(JAVA_NS_PREFIX)),
        "Java must survive, got " + migratedNsUris);
    assertTrue(
        migratedNsUris.stream().anyMatch(u -> u.startsWith(PCM_NS_PREFIX)),
        "PCM must survive the three-metamodel migration, got " + migratedNsUris);
  }

  @SuppressWarnings("UnstableApiUsage")
  private Set<String> seedThreeMetamodelVsum(Path sourceFolder) throws Exception {
    InternalVirtualModel vsum =
        Vsums.build(
            sourceFolder,
            UML_JAVA_PCM.createSpecifications(),
            new TestUserInteraction.ResultProvider(permissive()));
    try {
      TestViewFactory viewFactory = new TestViewFactory(vsum);
      View umlView = viewFactory.createViewOfElements("UML", Set.of(Model.class));
      viewFactory.changeViewRecordingChanges(
          umlView,
          committable ->
              committable.registerRoot(
                  LibraryExampleModel.build(),
                  URI.createFileURI(
                      sourceFolder.resolve("library.uml").toAbsolutePath().toString())));
      return nsUrisOf(Vsums.readRoots(vsum));
    } finally {
      vsum.dispose();
    }
  }
}
