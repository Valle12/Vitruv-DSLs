package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.emf.common.util.URI;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import pcm_mockup.Component;
import pcm_mockup.Pcm_mockupFactory;
import pcm_mockup.Repository;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;
import tools.vitruv.dsls.reactions.migration.interaction.DetectingUserInteraction;
import tools.vitruv.dsls.reactions.migration.preservation.PreservationPolicy;
import tools.vitruv.dsls.reactions.migration.spec.SpecificationSource;
import tools.vitruv.dsls.reactions.migration.strategy.ExplicitDominance;
import tools.vitruv.dsls.reactions.migration.testspecs.UmlPcmMockupSpecifications;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;
import uml_mockup.UClass;
import uml_mockup.UPackage;
import uml_mockup.Uml_mockupFactory;

@ExtendWith(RegisterMetamodelsInStandalone.class)
class SourceUpdateMigrationTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();
  private static final SpecificationSource OLD_ONE_DIRECTIONAL_SPECS =
      () -> List.of(UmlPcmMockupSpecifications.umlToPcm());
  private static final SpecificationSource NEW_BIDIRECTIONAL_SPECS =
      () -> List.of(UmlPcmMockupSpecifications.umlToPcm(), UmlPcmMockupSpecifications.pcmToUml());

  @TempDir Path tempDir;

  @BeforeAll
  static void prepareStandalone() {
    Vsums.prepareStandalone(ADAPTERS);
  }

  private static MigrationReport migrate(Path folder, MigrationSettings settings) {
    return new MigrationRunner(
            NEW_BIDIRECTIONAL_SPECS,
            new PropagationGraph(NEW_BIDIRECTIONAL_SPECS.createSpecifications()),
            new ExplicitDominance("uml_mockup"),
            null,
            ADAPTERS,
            settings)
        .run(folder);
  }

  private static Set<String> umlClassNames(Path folder) {
    UPackage shop =
        (UPackage) TwoMetamodelMockupMigrationTest.loadRoot(folder.resolve("model.uml_mockup"));
    return shop.getClasses().stream().map(UClass::getName).collect(Collectors.toUnmodifiableSet());
  }

  private static Set<String> pcmComponentNames(Path folder) {
    Repository repository =
        (Repository) TwoMetamodelMockupMigrationTest.loadRoot(folder.resolve("model.pcm_mockup"));
    return repository.getComponents().stream()
        .map(Component::getName)
        .collect(Collectors.toUnmodifiableSet());
  }

  @Test
  @DisplayName("backflow lifts derived-only information into the dominant and converges")
  void test1() throws Exception {
    Path folder = seedOldWorldWithPcmOnlyComponent();

    MigrationReport report = migrate(folder, MigrationSettings.defaults());

    assertTrue(report.migrated());
    assertTrue(report.sourceUpdate().attempted());
    assertTrue(report.sourceUpdate().converged(), "the backflow must reach a fixed point");
    assertTrue(report.sourceUpdate().rounds() >= 1, "one round must have been applied");
    assertEquals(
        List.of(
            "load",
            "roots",
            "dominance",
            "snapshot",
            "re-derive",
            "change-count",
            "source-update",
            "preserve",
            "refresh"),
        report.statistics().phaseNames());
    assertEquals(Set.of("Order", "LegacyBilling"), umlClassNames(folder));
    assertEquals(Set.of("Order", "LegacyBilling"), pcmComponentNames(folder));
  }

  @Test
  @DisplayName("forward-only migration without preservation drops derived-only information")
  void test2() throws Exception {
    Path folder = seedOldWorldWithPcmOnlyComponent();

    MigrationReport report =
        migrate(folder, MigrationSettings.forwardOnly().withPreservation(PreservationPolicy.OFF));

    assertTrue(report.migrated());
    assertFalse(report.sourceUpdate().attempted());
    assertFalse(report.preservation().attempted());
    assertEquals(Set.of("Order"), umlClassNames(folder));
    assertEquals(
        Set.of("Order"),
        pcmComponentNames(folder),
        "without source update the regenerated pcm reflects only the dominant uml");
  }

  @Test
  @DisplayName("forward-only migration re-attaches derived-only information no rule produced")
  void test5() throws Exception {
    Path folder = seedOldWorldWithPcmOnlyComponent();

    MigrationReport report = migrate(folder, MigrationSettings.forwardOnly());

    assertTrue(report.migrated());
    assertFalse(report.sourceUpdate().attempted());
    assertTrue(report.preservation().applied());
    assertEquals(
        Set.of("Order", "LegacyBilling"),
        pcmComponentNames(folder),
        "the hand-written component has no correspondence and must survive the re-derivation");
    assertEquals(
        Set.of("Order", "LegacyBilling"),
        umlClassNames(folder),
        "committing it through the view lets the backward reactions lift it into the source");
  }

  @Test
  @DisplayName("migration of a consistent state converges without applying any round")
  void test3() throws Exception {
    Path folder = seedConsistentWorld();

    MigrationReport report = migrate(folder, MigrationSettings.defaults());

    assertTrue(report.migrated());
    assertTrue(report.sourceUpdate().attempted());
    assertTrue(report.sourceUpdate().converged());
    assertEquals(0, report.sourceUpdate().rounds(), "nothing to feed back on consistent state");
    assertEquals(Set.of("Order"), umlClassNames(folder));
  }

  @Test
  @DisplayName("second migration is a stable fixed point")
  void test4() throws Exception {
    Path folder = seedOldWorldWithPcmOnlyComponent();
    migrate(folder, MigrationSettings.defaults());
    Set<String> umlAfterFirst = umlClassNames(folder);
    Set<String> pcmAfterFirst = pcmComponentNames(folder);

    MigrationReport second = migrate(folder, MigrationSettings.defaults());

    assertTrue(second.sourceUpdate().converged());
    assertEquals(umlAfterFirst, umlClassNames(folder));
    assertEquals(pcmAfterFirst, pcmComponentNames(folder));
  }

  private Path seedOldWorldWithPcmOnlyComponent() throws Exception {
    Path folder = seedConsistentWorld();
    InternalVirtualModel oldVsum =
        Vsums.build(
            folder,
            OLD_ONE_DIRECTIONAL_SPECS.createSpecifications(),
            new DetectingUserInteraction());
    try (View view = Vsums.openViewOfAll(oldVsum, "add-pcm-only-component")) {
      CommittableView committable = view.withChangeDerivingTrait();
      Repository repository =
          view.getRootObjects().stream()
              .filter(Repository.class::isInstance)
              .map(Repository.class::cast)
              .findFirst()
              .orElseThrow();
      Component legacy = Pcm_mockupFactory.eINSTANCE.createComponent();
      legacy.setName("LegacyBilling");
      repository.getComponents().add(legacy);
      committable.commitChanges();
    } finally {
      oldVsum.dispose();
    }

    assertEquals(Set.of("Order"), umlClassNames(folder), "the old reactions cannot lift pcm info");
    assertEquals(Set.of("Order", "LegacyBilling"), pcmComponentNames(folder));
    return folder;
  }

  @SuppressWarnings("UnstableApiUsage")
  private Path seedConsistentWorld() throws Exception {
    Path folder = Files.createDirectories(tempDir.resolve("vsum"));
    InternalVirtualModel vsum =
        Vsums.build(
            folder,
            OLD_ONE_DIRECTIONAL_SPECS.createSpecifications(),
            new DetectingUserInteraction());
    try (View view = Vsums.openEmptyView(vsum, "seed")) {
      CommittableView committable = view.withChangeDerivingTrait();
      UPackage shop = Uml_mockupFactory.eINSTANCE.createUPackage();
      shop.setName("shop");
      UClass order = Uml_mockupFactory.eINSTANCE.createUClass();
      order.setName("Order");
      shop.getClasses().add(order);
      committable.registerRoot(
          shop, URI.createFileURI(folder.resolve("model.uml_mockup").toString()));
      committable.commitChanges();
    } finally {
      vsum.dispose();
    }

    return folder;
  }
}
