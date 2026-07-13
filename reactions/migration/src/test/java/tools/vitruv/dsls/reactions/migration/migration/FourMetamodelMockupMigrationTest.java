package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import allElementTypes.AllElementTypesFactory;
import allElementTypes.NonRoot;
import allElementTypes.Root;
import allElementTypes2.Root2;
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
import tools.vitruv.dsls.reactions.migration.strategy.MaxReachabilityDominance;
import tools.vitruv.dsls.reactions.migration.testspecs.AllElementTypesMockupSpecifications;
import tools.vitruv.dsls.reactions.migration.testspecs.PcmAetMockupSpecifications;
import tools.vitruv.dsls.reactions.migration.testspecs.UmlPcmMockupSpecifications;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;
import uml_mockup.*;

@SuppressWarnings("UnstableApiUsage")
@ExtendWith(RegisterMetamodelsInStandalone.class)
class FourMetamodelMockupMigrationTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();
  private static final SpecificationSource CHAIN_SPECS =
      () ->
          List.of(
              UmlPcmMockupSpecifications.umlToPcm(),
              UmlPcmMockupSpecifications.pcmToUml(),
              PcmAetMockupSpecifications.pcmToAet(),
              PcmAetMockupSpecifications.aetToPcm(),
              AllElementTypesMockupSpecifications.toAllElementTypes2(),
              AllElementTypesMockupSpecifications.toAllElementTypes());
  private static final SpecificationSource DISCONNECTED_CLUSTER_SPECS =
      () ->
          List.of(
              UmlPcmMockupSpecifications.umlToPcm(),
              UmlPcmMockupSpecifications.pcmToUml(),
              AllElementTypesMockupSpecifications.toAllElementTypes2(),
              AllElementTypesMockupSpecifications.toAllElementTypes());

  @TempDir Path tempDir;

  @BeforeAll
  static void prepareStandalone() {
    Vsums.prepareStandalone(ADAPTERS);
  }

  private static MigrationReport migrate(
      Path folder, SpecificationSource specs, DominanceStrategy strategy) {
    return new MigrationRunner(
            specs,
            new PropagationGraph(specs.createSpecifications()),
            strategy,
            null,
            ADAPTERS,
            MigrationSettings.defaults())
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

  private static void assertAetMirrors(Path folder) {
    Root root =
        (Root) TwoMetamodelMockupMigrationTest.loadRoot(folder.resolve("model.allelementtypes"));
    assertEquals(
        List.of("Order"),
        root.getMultiValuedContainmentEReference().stream().map(NonRoot::getValue).toList());
  }

  @Test
  @DisplayName("seeding UML propagates across all four metamodels")
  void test1() throws Exception {
    Path folder = seededChainVsum();

    assertTrue(Files.exists(folder.resolve("model.pcm_mockup")));
    assertTrue(Files.exists(folder.resolve("model.allelementtypes")));
    assertTrue(Files.exists(folder.resolve("model.allelementtypes2")));
    Root2 root2 =
        (Root2) TwoMetamodelMockupMigrationTest.loadRoot(folder.resolve("model.allelementtypes2"));
    assertEquals(1, root2.getMultiValuedContainmentEReference2().size());
  }

  @Test
  @DisplayName("chain end dominant regenerates all three derived metamodels")
  void test2() throws Exception {
    Path folder = seededChainVsum();

    MigrationReport report = migrate(folder, CHAIN_SPECS, new ExplicitDominance("uml_mockup"));

    assertTrue(report.migrated());
    assertEquals(1, report.sources().size());
    assertEquals(3, report.derived().size(), "pcm, aet, and aet2 are all re-derived");
    TwoMetamodelMockupMigrationTest.assertShopUmlModel(folder);
    TwoMetamodelMockupMigrationTest.assertShopPcmModel(folder);
    assertAetMirrors(folder);
    assertTrue(Files.exists(folder.resolve("model.allelementtypes2")));
  }

  @Test
  @DisplayName("disconnected clusters are migrated with a two-source covering set")
  void test3() throws Exception {
    Path folder = seededDisconnectedClustersVsum();

    MigrationReport report =
        migrate(folder, DISCONNECTED_CLUSTER_SPECS, new MaxReachabilityDominance());

    assertTrue(report.migrated());
    assertEquals(2, report.sources().size(), "one source of truth per disconnected cluster");
    assertEquals(2, report.derived().size(), "the other member of each cluster is re-derived");
    assertTrue(Files.exists(folder.resolve("model.uml_mockup")));
    assertTrue(Files.exists(folder.resolve("model.pcm_mockup")));
    assertTrue(Files.exists(folder.resolve("payments.allelementtypes")));
    assertTrue(Files.exists(folder.resolve("payments.allelementtypes2")));
  }

  private Path seededChainVsum() throws Exception {
    Path folder = Files.createDirectories(tempDir.resolve("chain"));
    InternalVirtualModel vsum =
        Vsums.build(folder, CHAIN_SPECS.createSpecifications(), new DetectingUserInteraction());
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

  private Path seededDisconnectedClustersVsum() throws Exception {
    Path folder = Files.createDirectories(tempDir.resolve("clusters"));
    InternalVirtualModel vsum =
        Vsums.build(
            folder,
            DISCONNECTED_CLUSTER_SPECS.createSpecifications(),
            new DetectingUserInteraction());
    try (View view = Vsums.openEmptyView(vsum, "seed")) {
      CommittableView committable = view.withChangeDerivingTrait();
      committable.registerRoot(
          shopPackage(), URI.createFileURI(folder.resolve("model.uml_mockup").toString()));
      Root payments = AllElementTypesFactory.eINSTANCE.createRoot();
      NonRoot targetAccount = AllElementTypesFactory.eINSTANCE.createNonRoot();
      targetAccount.setValue("TargetAccount");
      payments.getMultiValuedContainmentEReference().add(targetAccount);
      committable.registerRoot(
          payments, URI.createFileURI(folder.resolve("payments.allelementtypes").toString()));
      committable.commitChanges();
    } finally {
      vsum.dispose();
    }

    return folder;
  }
}
