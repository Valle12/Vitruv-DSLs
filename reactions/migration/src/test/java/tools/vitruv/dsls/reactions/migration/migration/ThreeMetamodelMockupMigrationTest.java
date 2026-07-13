package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import allElementTypes.NonRoot;
import allElementTypes.Root;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.emf.common.util.URI;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;
import tools.vitruv.dsls.reactions.migration.interaction.DetectingUserInteraction;
import tools.vitruv.dsls.reactions.migration.spec.SpecificationSource;
import tools.vitruv.dsls.reactions.migration.strategy.DominanceStrategy;
import tools.vitruv.dsls.reactions.migration.strategy.ExplicitDominance;
import tools.vitruv.dsls.reactions.migration.strategy.FewestChangesDominance;
import tools.vitruv.dsls.reactions.migration.testspecs.PcmAetMockupSpecifications;
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
class ThreeMetamodelMockupMigrationTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();
  private static final SpecificationSource SPECS =
      () ->
          List.of(
              UmlPcmMockupSpecifications.umlToPcm(),
              UmlPcmMockupSpecifications.pcmToUml(),
              PcmAetMockupSpecifications.pcmToAet(),
              PcmAetMockupSpecifications.aetToPcm());

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

  private static void assertAetMirrorsShop(Path folder) {
    Path aetFile = folder.resolve("model.allelementtypes");
    assertTrue(Files.exists(aetFile), "aet model must exist at " + aetFile);
    Root root = (Root) TwoMetamodelMockupMigrationTest.loadRoot(aetFile);
    assertEquals(
        List.of("Order"),
        root.getMultiValuedContainmentEReference().stream().map(NonRoot::getValue).toList());
  }

  @Test
  @DisplayName("seeding UML propagates transitively to the third metamodel")
  void test1() throws Exception {
    Path folder = seededVsum();

    assertAetMirrorsShop(folder);
  }

  @Test
  @DisplayName("UML dominant regenerates both derived metamodels")
  void test2() throws Exception {
    Path folder = seededVsum();

    MigrationReport report = migrate(folder, new ExplicitDominance("uml_mockup"));

    assertTrue(report.migrated());
    assertEquals(1, report.sources().size());
    assertEquals(2, report.derived().size());
    TwoMetamodelMockupMigrationTest.assertShopUmlModel(folder);
    TwoMetamodelMockupMigrationTest.assertShopPcmModel(folder);
    assertAetMirrorsShop(folder);
  }

  @Test
  @DisplayName("middle of the chain as dominant also covers everything")
  void test3() throws Exception {
    Path folder = seededVsum();

    MigrationReport report = migrate(folder, new ExplicitDominance("pcm_mockup"));

    assertTrue(report.migrated());
    assertEquals(1, report.sources().size(), "pcm reaches uml and aet directly");
    assertEquals(2, report.derived().size());
    TwoMetamodelMockupMigrationTest.assertShopUmlModel(folder);
    assertAetMirrorsShop(folder);
  }

  @Test
  @DisplayName("fewest changes strategy runs trials across all candidates")
  void test4() throws Exception {
    Path folder = seededVsum();

    MigrationReport report = migrate(folder, new FewestChangesDominance());

    assertTrue(report.migrated());
    TwoMetamodelMockupMigrationTest.assertShopUmlModel(folder);
    TwoMetamodelMockupMigrationTest.assertShopPcmModel(folder);
    assertAetMirrorsShop(folder);
  }

  @SuppressWarnings("UnstableApiUsage")
  private Path seededVsum() throws Exception {
    Path folder = Files.createDirectories(tempDir.resolve("vsum"));
    InternalVirtualModel vsum =
        Vsums.build(folder, SPECS.createSpecifications(), new DetectingUserInteraction());
    try (View view = Vsums.openEmptyView(vsum, "seed")) {
      CommittableView committable = view.withChangeDerivingTrait();
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
      committable.registerRoot(
          shop, URI.createFileURI(folder.resolve("model.uml_mockup").toString()));
      committable.commitChanges();
    } finally {
      vsum.dispose();
    }

    return folder;
  }
}
