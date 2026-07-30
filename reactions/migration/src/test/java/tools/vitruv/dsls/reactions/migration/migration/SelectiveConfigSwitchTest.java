package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tools.vitruv.dsls.reactions.migration.migration.LibraryCliFixture.permissiveInteraction;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.DataType;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.PrimitiveType;
import org.eclipse.uml2.uml.UMLFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.applications.util.temporary.java.JavaSetup;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.dsls.reactions.migration.PropagationJars;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;
import tools.vitruv.dsls.reactions.migration.spec.JarSpecificationSource;
import tools.vitruv.dsls.reactions.migration.strategy.ExplicitDominance;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.testutils.integration.TestViewFactory;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

@Slf4j
@ExtendWith(RegisterMetamodelsInStandalone.class)
class SelectiveConfigSwitchTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();

  @TempDir Path tempDir;

  @BeforeAll
  static void prepareStandalone() {
    Vsums.prepareStandalone(ADAPTERS);
  }

  private static Model buildSelfContainedModel() {
    Model model = UMLFactory.eINSTANCE.createModel();
    model.setName("catalogue");

    PrimitiveType intType = UMLFactory.eINSTANCE.createPrimitiveType();
    intType.setName("int");
    model.getPackagedElements().add(intType);

    org.eclipse.uml2.uml.Package catalog = model.createNestedPackage("catalog");

    DataType isbn = UMLFactory.eINSTANCE.createDataType();
    isbn.setName("Isbn");
    catalog.getPackagedElements().add(isbn);
    isbn.createOwnedAttribute("code", intType);

    Class media = catalog.createOwnedClass("Media", false);
    media.createOwnedAttribute("mediaId", intType);

    Class book = catalog.createOwnedClass("Book", false);
    book.createGeneralization(media);
    book.createOwnedAttribute("pageCount", intType);
    book.createOwnedAttribute("isbn", isbn);

    return model;
  }

  @BeforeEach
  void resetJavaClasspath() {
    JavaSetup.resetClasspathAndRegisterStandardLibrary();
  }

  @Test
  @DisplayName("a selective repropagation completes the configuration switch")
  void test1() throws Exception {
    Path vsumFolder = seedWithConfig1();

    MigrationReport report = migrateToConfig7(vsumFolder);

    assertTrue(report.selective().attempted(), "the selective path must be tried");
    assertFalse(
        report.selective().fellBackToFull(),
        "the tear-down must not give up: " + report.selective().fallbackReason().orElse(""));
    assertTrue(
        report.selective().affectedElementCount() > 0,
        "a configuration switch must repropagate a non-empty set of affected elements");
    assertTrue(report.migrated(), "the repropagation is a migration");

    Map<String, String> java = ModelStructure.ofJava(vsumFolder, ADAPTERS);
    log.info("derived after the selective switch: {}", java);
    assertTrue(
        java.get("Media").startsWith("interface"),
        "config7 realises a uml::Class as a Java interface: " + java.get("Media"));
  }

  @SuppressWarnings("UnstableApiUsage")
  private Path seedWithConfig1() throws Exception {
    Path vsumFolder = Files.createDirectories(tempDir.resolve("catalogue-vsum"));
    try (JarSpecificationSource specifications =
        JarSpecificationSource.fromJar(PropagationJars.config1())) {
      InternalVirtualModel vsum =
          Vsums.build(
              vsumFolder,
              specifications.createSpecifications(),
              new TestUserInteraction.ResultProvider(permissiveInteraction()));
      try {
        TestViewFactory viewFactory = new TestViewFactory(vsum);
        View umlView = viewFactory.createViewOfElements("UML", Set.of(Model.class));
        viewFactory.changeViewRecordingChanges(
            umlView,
            committable ->
                committable.registerRoot(
                    buildSelfContainedModel(),
                    URI.createFileURI(
                        vsumFolder
                            .resolve("model")
                            .resolve("catalogue.uml")
                            .toAbsolutePath()
                            .toString())));
      } finally {
        vsum.dispose();
      }
    }

    return vsumFolder;
  }

  private MigrationReport migrateToConfig7(Path vsumFolder) throws Exception {
    try (JarSpecificationSource specifications =
        JarSpecificationSource.fromJar(PropagationJars.config7())) {
      return new MigrationRunner(
              specifications,
              new PropagationGraph(specifications.createSpecifications()),
              new ExplicitDominance("uml"),
              new TestUserInteraction.ResultProvider(permissiveInteraction()),
              ADAPTERS,
              MigrationSettings.forwardOnly()
                  .withMode(MigrationMode.ID_DIFF)
                  .withPreservation(
                      tools.vitruv.dsls.reactions.migration.preservation.PreservationPolicy.OFF))
          .run(vsumFolder);
    }
  }

  @Test
  @DisplayName("the seeded model refers to nothing outside itself")
  void test2() throws Exception {
    Path vsumFolder = seedWithConfig1();

    assertEquals(
        ModelStructure.modelFiles(vsumFolder.resolve("src"), ".java").size(),
        ModelStructure.modelFiles(vsumFolder, ".java").size(),
        "no derived Java may sit outside src");
    Map<String, String> java = ModelStructure.ofJava(vsumFolder, ADAPTERS);
    log.info("derived under config1: {}", java.keySet());
    assertTrue(java.containsKey("Media") && java.containsKey("Book") && java.containsKey("Isbn"));
  }
}
