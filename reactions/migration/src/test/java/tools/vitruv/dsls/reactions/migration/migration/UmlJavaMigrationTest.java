package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.uml2.uml.Model;
import org.emftext.language.java.JavaClasspath;
import org.emftext.language.java.classifiers.ConcreteClassifier;
import org.emftext.language.java.containers.CompilationUnit;
import org.emftext.language.java.containers.ContainersFactory;
import org.emftext.language.java.containers.Package;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.applications.umljava.JavaToUmlChangePropagationSpecification;
import tools.vitruv.applications.umljava.UmlToJavaChangePropagationSpecification;
import tools.vitruv.applications.util.temporary.java.JavaContainerAndClassifierUtil;
import tools.vitruv.applications.util.temporary.java.JavaMemberAndParameterUtil;
import tools.vitruv.applications.util.temporary.java.JavaModificationUtil;
import tools.vitruv.applications.util.temporary.java.JavaPersistenceHelper;
import tools.vitruv.applications.util.temporary.java.JavaSetup;
import tools.vitruv.applications.util.temporary.java.JavaVisibility;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;
import tools.vitruv.dsls.reactions.migration.spec.SpecificationSource;
import tools.vitruv.dsls.reactions.migration.strategy.DominanceStrategy;
import tools.vitruv.dsls.reactions.migration.strategy.ExplicitDominance;
import tools.vitruv.dsls.reactions.migration.strategy.FewestChangesDominance;
import tools.vitruv.dsls.reactions.migration.strategy.MaxReachabilityDominance;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.testutils.integration.TestViewFactory;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

@ExtendWith(RegisterMetamodelsInStandalone.class)
class UmlJavaMigrationTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();
  private static final SpecificationSource SPECS =
      () ->
          List.of(
              new UmlToJavaChangePropagationSpecification(),
              new JavaToUmlChangePropagationSpecification());
  private static final String JAVA_NS_URI_PREFIX = "http://www.emftext.org/java";
  private static final String UML_NS_URI = "http://www.eclipse.org/uml2/5.0.0/UML";
  private static final String CLASS_NAME = "Greeter";

  @TempDir Path tempDir;

  private Path sourceFolder;
  private InternalVirtualModel sourceVsum;

  @BeforeAll
  static void prepareStandalone() {
    Vsums.prepareStandalone(ADAPTERS);
  }

  private static TestUserInteraction permissiveInteraction() {
    TestUserInteraction interaction = new TestUserInteraction();
    interaction.onTextInput(description -> true).always().respondWith("model");
    interaction
        .onMultipleChoiceSingleSelection(description -> true)
        .always()
        .respondWithChoiceAt(0);
    interaction.onConfirmation(description -> true).always().respondWith(true);
    interaction.acknowledgeNotification(description -> true);
    return interaction;
  }

  private static Set<String> nsUrisOf(List<EObject> roots) {
    return roots.stream()
        .map(root -> root.eClass().getEPackage().getNsURI())
        .collect(Collectors.toUnmodifiableSet());
  }

  private static Set<String> umlClassNames(Model model) {
    return model.getPackagedElements().stream()
        .filter(org.eclipse.uml2.uml.Class.class::isInstance)
        .map(org.eclipse.uml2.uml.Class.class::cast)
        .map(org.eclipse.uml2.uml.Class::getName)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static boolean isJdkStandardLibraryFile(Path path) {
    String normalized = path.toString().replace('\\', '/');
    return normalized.contains("/java/")
        || normalized.contains("/javax/")
        || normalized.contains("/sun/")
        || normalized.contains("/jdk/")
        || normalized.contains("/com/sun/");
  }

  @BeforeEach
  void buildSeedVsum() throws IOException {
    JavaSetup.resetClasspathAndRegisterStandardLibrary();
    sourceFolder = Files.createDirectories(tempDir.resolve("source-vsum"));
    sourceVsum =
        Vsums.build(
            sourceFolder,
            SPECS.createSpecifications(),
            new TestUserInteraction.ResultProvider(permissiveInteraction()));
  }

  @AfterEach
  void disposeSeedVsum() {
    if (sourceVsum != null) {
      sourceVsum.dispose();
      sourceVsum = null;
    }
  }

  @Test
  @DisplayName("seeding a Java class propagates to UML")
  void test1() throws Exception {
    seedJavaClass();

    Set<String> seededNsUris = nsUrisOf(Vsums.readRoots(sourceVsum));
    assertTrue(
        seededNsUris.contains(UML_NS_URI), "Java -> UML reactions must create the UML model");
    assertTrue(seededNsUris.stream().anyMatch(nsUri -> nsUri.startsWith(JAVA_NS_URI_PREFIX)));
  }

  @Test
  @DisplayName("UML dominant keeps UML and regenerates Java")
  void test2() throws Exception {
    seedJavaClass();
    Set<String> originalUmlClasses = umlClassNames(firstUmlModel(sourceFolder));

    MigrationReport report = migrate(new ExplicitDominance("uml"));

    assertTrue(report.migrated());
    assertEquals(originalUmlClasses, umlClassNames(firstUmlModel(sourceFolder)));
    Set<String> nsUris = nsUrisOf(loadAllModelRoots(sourceFolder));
    assertTrue(nsUris.contains(UML_NS_URI), "the dominant UML must still be present");
    assertTrue(
        nsUris.stream().anyMatch(nsUri -> nsUri.startsWith(JAVA_NS_URI_PREFIX)),
        "the Java model must be regenerated from the dominant UML");
  }

  @Test
  @DisplayName("Java dominant keeps Java and regenerates UML")
  void test3() throws Exception {
    seedJavaClass();

    MigrationReport report = migrate(new ExplicitDominance("java"));

    assertTrue(report.migrated());
    Set<String> nsUris = nsUrisOf(loadAllModelRoots(sourceFolder));
    assertTrue(
        nsUris.stream().anyMatch(nsUri -> nsUri.startsWith(JAVA_NS_URI_PREFIX)),
        "the dominant Java model must still be present");
    assertTrue(nsUris.contains(UML_NS_URI), "the UML model must be regenerated from Java");
    assertTrue(
        umlClassNames(firstUmlModel(sourceFolder)).contains(CLASS_NAME),
        "the regenerated UML must contain the class derived from the Java source of truth");
  }

  @Test
  @DisplayName("Java dominant with a JDK-typed field regenerates UML")
  void test4() throws Exception {
    org.emftext.language.java.classifiers.Class holder =
        JavaContainerAndClassifierUtil.createJavaClass(
            "Holder", JavaVisibility.PUBLIC, false, false);
    ConcreteClassifier stringClass =
        (ConcreteClassifier) JavaClasspath.get().getClassifier("java.lang.String");
    holder
        .getMembers()
        .add(
            JavaMemberAndParameterUtil.createJavaAttribute(
                "label",
                JavaModificationUtil.createNamespaceClassifierReference(stringClass),
                JavaVisibility.PRIVATE,
                false,
                false));
    seedJavaClassifiers(holder);

    MigrationReport report = migrate(new ExplicitDominance("java"));

    assertTrue(report.migrated());
    assertTrue(umlClassNames(firstUmlModel(sourceFolder)).contains("Holder"));
  }

  @Test
  @DisplayName("reachability strategy migrates")
  void test5() throws Exception {
    seedJavaClass();

    assertTrue(migrate(new MaxReachabilityDominance()).migrated());
    assertTrue(umlClassNames(firstUmlModel(sourceFolder)).contains(CLASS_NAME));
  }

  @Test
  @DisplayName("fewest changes strategy migrates")
  void test6() throws Exception {
    seedJavaClass();

    assertTrue(migrate(new FewestChangesDominance()).migrated());
    assertTrue(umlClassNames(firstUmlModel(sourceFolder)).contains(CLASS_NAME));
  }

  @Test
  @DisplayName("source update backflow converges for a UML dominant migration")
  void test7() throws Exception {
    seedJavaClass();

    MigrationReport report = migrate(new ExplicitDominance("uml"), MigrationSettings.defaults());

    assertTrue(report.migrated());
    assertTrue(report.sourceUpdate().attempted());
    assertTrue(
        report.sourceUpdate().converged(),
        "the backflow over JaMoPP models must reach a fixed point, but stopped after "
            + report.sourceUpdate().rounds()
            + " round(s) with delta "
            + report.sourceUpdate().lastDeltaSize());
    assertTrue(umlClassNames(firstUmlModel(sourceFolder)).contains(CLASS_NAME));
  }

  private MigrationReport migrate(DominanceStrategy strategy) {
    return migrate(strategy, MigrationSettings.forwardOnly());
  }

  private MigrationReport migrate(DominanceStrategy strategy, MigrationSettings settings) {
    sourceVsum.dispose();
    sourceVsum = null;
    return new MigrationRunner(
            SPECS,
            new PropagationGraph(SPECS.createSpecifications()),
            strategy,
            new TestUserInteraction.ResultProvider(permissiveInteraction()),
            ADAPTERS,
            settings)
        .run(sourceFolder);
  }

  private void seedJavaClass() throws Exception {
    seedJavaClassifiers(
        JavaContainerAndClassifierUtil.createJavaClass(
            UmlJavaMigrationTest.CLASS_NAME, JavaVisibility.PUBLIC, false, false));
  }

  @SuppressWarnings("UnstableApiUsage")
  private void seedJavaClassifiers(ConcreteClassifier... classifiers) throws Exception {
    Map<URI, List<EObject>> rootsByUri = new LinkedHashMap<>();
    for (ConcreteClassifier classifier : classifiers) {
      CompilationUnit compilationUnit = ContainersFactory.eINSTANCE.createCompilationUnit();
      JavaContainerAndClassifierUtil.updateCompilationUnitName(
          compilationUnit, classifier.getName());
      compilationUnit.getClassifiers().add(classifier);
      URI uri =
          URI.createFileURI(
              sourceFolder
                  .resolve(JavaPersistenceHelper.buildJavaFilePath(compilationUnit))
                  .toString());
      rootsByUri.put(uri, List.of(compilationUnit));
    }

    DetachedReferences detached = CrossResourceReferences.detach(rootsByUri);
    TestViewFactory viewFactory = new TestViewFactory(sourceVsum);
    View javaView =
        viewFactory.createViewOfElements(
            "Java packages and classes", Set.of(Package.class, CompilationUnit.class));
    viewFactory.changeViewRecordingChanges(
        javaView,
        committable -> {
          rootsByUri.forEach((uri, roots) -> committable.registerRoot(roots.get(0), uri));
          detached.reattach();
        });
  }

  private Model firstUmlModel(Path folder) throws IOException {
    return loadModelResources(folder, ".uml").stream()
        .flatMap(resource -> resource.getContents().stream())
        .filter(Model.class::isInstance)
        .map(Model.class::cast)
        .findFirst()
        .orElseThrow(() -> new AssertionError("no UML Model persisted in " + folder));
  }

  private List<EObject> loadAllModelRoots(Path folder) throws IOException {
    List<EObject> roots = new ArrayList<>();
    for (Resource resource : loadModelResources(folder, ".uml")) {
      roots.addAll(resource.getContents());
    }
    for (Resource resource : loadModelResources(folder, ".java")) {
      roots.addAll(resource.getContents());
    }

    return roots;
  }

  private List<Resource> loadModelResources(Path folder, String extension) throws IOException {
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet.getLoadOptions().putAll(ADAPTERS.combinedLoadOptions());
    List<Resource> resources = new ArrayList<>();
    try (var walk = Files.walk(folder)) {
      walk.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(extension))
          .filter(path -> !isJdkStandardLibraryFile(path))
          .forEach(
              path -> {
                Resource resource =
                    resourceSet.getResource(URI.createFileURI(path.toString()), true);
                assertTrue(
                    resource.getErrors().isEmpty(), "model must load without errors: " + path);
                resources.add(resource);
              });
    }

    return resources;
  }
}
