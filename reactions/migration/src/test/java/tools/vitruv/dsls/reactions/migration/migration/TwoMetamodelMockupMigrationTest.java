package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import pcm_mockup.PInterface;
import pcm_mockup.PNamedElement;
import pcm_mockup.Repository;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;
import tools.vitruv.dsls.reactions.migration.interaction.DetectingUserInteraction;
import tools.vitruv.dsls.reactions.migration.spec.SpecificationSource;
import tools.vitruv.dsls.reactions.migration.strategy.DominanceStrategy;
import tools.vitruv.dsls.reactions.migration.strategy.ExplicitDominance;
import tools.vitruv.dsls.reactions.migration.strategy.FewestChangesDominance;
import tools.vitruv.dsls.reactions.migration.strategy.MaxReachabilityDominance;
import tools.vitruv.dsls.reactions.migration.testspecs.UmlPcmMockupSpecifications;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;
import uml_mockup.UClass;
import uml_mockup.UInterface;
import uml_mockup.UMethod;
import uml_mockup.UPackage;
import uml_mockup.Uml_mockupFactory;

@ExtendWith(RegisterMetamodelsInStandalone.class)
class TwoMetamodelMockupMigrationTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();
  private static final SpecificationSource SPECS =
      () -> List.of(UmlPcmMockupSpecifications.umlToPcm(), UmlPcmMockupSpecifications.pcmToUml());

  @TempDir Path tempDir;

  @BeforeAll
  static void prepareStandalone() {
    Vsums.prepareStandalone(ADAPTERS);
  }

  private static MigrationReport migrate(Path folder, DominanceStrategy strategy) {
    return new MigrationRunner(
            SPECS,
            new PropagationGraph(SPECS.createSpecifications()),
            strategy,
            null,
            ADAPTERS,
            MigrationSettings.forwardOnly())
        .run(folder);
  }

  private static UPackage shopPackage() {
    UPackage shop = Uml_mockupFactory.eINSTANCE.createUPackage();
    shop.setName("shop");
    UClass order = Uml_mockupFactory.eINSTANCE.createUClass();
    order.setName("Order");
    shop.getClasses().add(order);
    UInterface api = Uml_mockupFactory.eINSTANCE.createUInterface();
    api.setName("Api");
    UMethod get = Uml_mockupFactory.eINSTANCE.createUMethod();
    get.setName("get");
    api.getMethods().add(get);
    shop.getInterfaces().add(api);
    return shop;
  }

  static void assertShopUmlModel(Path folder) {
    Path umlFile = folder.resolve("model.uml_mockup");
    assertTrue(Files.exists(umlFile), "uml model must exist at " + umlFile);
    UPackage shop = (UPackage) loadRoot(umlFile);
    assertEquals("shop", shop.getName());
    assertEquals(List.of("Order"), shop.getClasses().stream().map(UClass::getName).toList());
    assertEquals(List.of("Api"), shop.getInterfaces().stream().map(UInterface::getName).toList());
  }

  static void assertShopPcmModel(Path folder) {
    Path pcmFile = folder.resolve("model.pcm_mockup");
    assertTrue(Files.exists(pcmFile), "pcm model must exist at " + pcmFile);
    Repository repository = (Repository) loadRoot(pcmFile);
    assertEquals("shop", repository.getName());
    assertEquals(
        List.of("Order"), repository.getComponents().stream().map(PNamedElement::getName).toList());
    assertEquals(
        List.of("Api"), repository.getInterfaces().stream().map(PInterface::getName).toList());
  }

  static void assertVsumMetadataRegenerated(Path folder) {
    assertTrue(Files.exists(folder.resolve("vsum").resolve("correspondences.correspondence")));
    assertTrue(Files.exists(folder.resolve("vsum").resolve("models.models")));
  }

  static void assertVsumReloads(Path folder) {
    InternalVirtualModel reloaded =
        Vsums.build(folder, SPECS.createSpecifications(), new DetectingUserInteraction());
    try {
      assertFalse(Vsums.readRoots(reloaded).isEmpty(), "reloaded VSUM must expose model roots");
    } finally {
      reloaded.dispose();
    }
  }

  static EObject loadRoot(Path modelFile) {
    return new ResourceSetImpl()
        .getResource(URI.createFileURI(modelFile.toString()), true)
        .getContents()
        .getFirst();
  }

  @Test
  @DisplayName("UML dominant keeps UML and regenerates PCM")
  void test1() throws Exception {
    Path folder = seededVsum();

    MigrationReport report = migrate(folder, new ExplicitDominance("uml_mockup"));

    assertTrue(report.migrated());
    assertEquals(1, report.sources().size());
    assertEquals(1, report.derived().size());
    assertTrue(report.sources().getFirst().owns(uml_mockup.Uml_mockupPackage.eNS_URI));
    assertShopUmlModel(folder);
    assertShopPcmModel(folder);
    assertVsumMetadataRegenerated(folder);
    assertVsumReloads(folder);
  }

  @Test
  @DisplayName("PCM dominant keeps PCM and regenerates UML")
  void test2() throws Exception {
    Path folder = seededVsum();

    MigrationReport report = migrate(folder, new ExplicitDominance("pcm_mockup"));

    assertTrue(report.migrated());
    assertTrue(report.sources().getFirst().owns(pcm_mockup.Pcm_mockupPackage.eNS_URI));
    assertShopUmlModel(folder);
    assertShopPcmModel(folder);
    assertVsumReloads(folder);
  }

  @Test
  @DisplayName("reachability strategy migrates")
  void test3() throws Exception {
    Path folder = seededVsum();

    MigrationReport report = migrate(folder, new MaxReachabilityDominance());

    assertTrue(report.migrated());
    assertShopUmlModel(folder);
    assertShopPcmModel(folder);
  }

  @Test
  @DisplayName("fewest changes strategy executes trials and migrates")
  void test4() throws Exception {
    Path folder = seededVsum();

    MigrationReport report = migrate(folder, new FewestChangesDominance());

    assertTrue(report.migrated());
    assertShopUmlModel(folder);
    assertShopPcmModel(folder);
  }

  @Test
  @DisplayName("empty folder is nothing to do")
  void test5() throws Exception {
    Path folder = Files.createDirectories(tempDir.resolve("empty"));

    MigrationReport report = migrate(folder, new ExplicitDominance("uml_mockup"));

    assertFalse(report.migrated());
  }

  @SuppressWarnings("UnstableApiUsage")
  private Path seededVsum() throws Exception {
    Path folder = Files.createDirectories(tempDir.resolve("vsum"));
    InternalVirtualModel vsum =
        Vsums.build(folder, SPECS.createSpecifications(), new DetectingUserInteraction());
    try (View view = Vsums.openEmptyView(vsum, "seed")) {
      CommittableView committable = view.withChangeDerivingTrait();
      committable.registerRoot(
          shopPackage(), URI.createFileURI(folder.resolve("model.uml_mockup").toString()));
      committable.commitChanges();
    } finally {
      vsum.dispose();
    }

    return folder;
  }
}
