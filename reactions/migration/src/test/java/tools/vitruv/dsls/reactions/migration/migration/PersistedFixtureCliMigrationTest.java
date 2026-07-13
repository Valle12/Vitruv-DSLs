package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.NamedElement;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.applications.umljava.UmlToJavaChangePropagationSpecification;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.dsls.reactions.migration.Main;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.interaction.DetectingUserInteraction;
import tools.vitruv.dsls.reactions.migration.spec.JarSpecificationSource;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

@ExtendWith(RegisterMetamodelsInStandalone.class)
class PersistedFixtureCliMigrationTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();

  @TempDir Path tempDir;

  @BeforeAll
  static void prepareStandalone() {
    Vsums.prepareStandalone(ADAPTERS);
  }

  private static void assertShopModelsAreIntact(Path vsumFolder) throws Exception {
    Path umlFile = vsumFolder.resolve("model").resolve("model.uml");
    assertTrue(Files.exists(umlFile), "the dominant UML model must exist");
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet.getLoadOptions().putAll(ADAPTERS.combinedLoadOptions());
    Resource umlResource = resourceSet.getResource(URI.createFileURI(umlFile.toString()), true);
    assertTrue(umlResource.getErrors().isEmpty());
    Model model =
        umlResource.getContents().stream()
            .filter(Model.class::isInstance)
            .map(Model.class::cast)
            .findFirst()
            .orElseThrow();
    Set<String> classifierNames =
        model.getPackagedElements().stream()
            .filter(org.eclipse.uml2.uml.Classifier.class::isInstance)
            .map(NamedElement::getName)
            .collect(Collectors.toSet());
    for (String expected :
        List.of("Identifiable", "OrderStatus", "Customer", "PremiumCustomer", "Order")) {
      assertTrue(classifierNames.contains(expected), "UML must contain " + expected);
    }

    try (var javaFiles = Files.walk(vsumFolder.resolve("src"))) {
      assertFalse(
          javaFiles.filter(path -> path.toString().endsWith(".java")).toList().isEmpty(),
          "the Java side must be regenerated");
    }
  }

  private static void assertVsumReloadsWithJarSpecs(Path vsumFolder, Path umljavaJar)
      throws Exception {
    InternalVirtualModel reloaded =
        Vsums.build(
            vsumFolder,
            JarSpecificationSource.fromJar(umljavaJar).createSpecifications(),
            new DetectingUserInteraction());
    try {
      assertFalse(Vsums.readRoots(reloaded).isEmpty(), "the migrated VSUM must reload");
    } finally {
      reloaded.dispose();
    }
  }

  private static Path jarContaining() throws Exception {
    return Path.of(
        UmlToJavaChangePropagationSpecification.class
            .getProtectionDomain()
            .getCodeSource()
            .getLocation()
            .toURI());
  }

  @Test
  @DisplayName("CLI migrates the committed persisted shop VSUM in place")
  void test1() throws Exception {
    Path umljavaJar = jarContaining();
    Assumptions.assumeTrue(
        umljavaJar.toString().endsWith(".jar"), "umljava must be packaged as a jar to scan");
    Path vsumFolder = materializedFixture();

    int exitCode =
        Main.run(
            new String[] {
              "--model",
              vsumFolder.toString(),
              "--propagations",
              umljavaJar.toString(),
              "--dominant",
              "uml",
              "--backup"
            });

    assertEquals(0, exitCode, "the CLI migration must succeed");
    assertShopModelsAreIntact(vsumFolder);
    assertVsumReloadsWithJarSpecs(vsumFolder, umljavaJar);

    int secondRun =
        Main.run(
            new String[] {
              "--model", vsumFolder.toString(),
              "--propagations", umljavaJar.toString(),
              "--dominant", "uml"
            });
    assertEquals(0, secondRun, "a second migration of the migrated VSUM must also succeed");
    assertShopModelsAreIntact(vsumFolder);
  }

  private Path materializedFixture() throws Exception {
    Path fixture =
        Path.of(
            Objects.requireNonNull(getClass().getResource(ShopVsumFixtureGenerator.RESOURCE_PATH))
                .toURI());
    Path vsumFolder = tempDir.resolve("shop-vsum");
    ShopVsumFixtureGenerator.copyTreeReplacing(
        fixture,
        vsumFolder,
        ShopVsumFixtureGenerator.SENTINEL,
        URI.createFileURI(vsumFolder.toAbsolutePath().toString()).toString());
    return vsumFolder;
  }
}
