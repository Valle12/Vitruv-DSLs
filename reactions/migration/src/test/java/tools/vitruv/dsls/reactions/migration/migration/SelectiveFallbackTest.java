package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.CLASS_HASH;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.CLASS_INSERTED;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.CLASS_TRIGGER;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.METHOD_HASH;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.METHOD_INSERTED;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.ROOT_HASH;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.ROOT_INSERTED;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.ROOT_INSERTED_V2;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.ROOT_TRIGGER;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.change.propagation.ConsistencyRuleTrigger;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;
import tools.vitruv.dsls.reactions.migration.interaction.DetectingUserInteraction;
import tools.vitruv.dsls.reactions.migration.preservation.PreservationPolicy;
import tools.vitruv.dsls.reactions.migration.spec.SpecificationSource;
import tools.vitruv.dsls.reactions.migration.strategy.ExplicitDominance;
import tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications;
import tools.vitruv.dsls.reactions.migration.testspecs.UmlPcmMockupSpecifications;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;
import uml_mockup.UClass;
import uml_mockup.UPackage;
import uml_mockup.Uml_mockupFactory;

@ExtendWith(RegisterMetamodelsInStandalone.class)
class SelectiveFallbackTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();
  private static final String HAND_WRITTEN_COMPONENT = "Ledger";

  @TempDir Path tempDir;

  @BeforeAll
  static void prepareStandalone() {
    Vsums.prepareStandalone(ADAPTERS);
  }

  private static MigrationReport migrate(Path folder, SpecificationSource newSpecifications) {
    return migrate(folder, newSpecifications, PreservationPolicy.OFF);
  }

  private static MigrationReport migrate(
      Path folder, SpecificationSource newSpecifications, PreservationPolicy policy) {
    return new MigrationRunner(
            newSpecifications,
            new PropagationGraph(newSpecifications.createSpecifications()),
            new ExplicitDominance("uml_mockup"),
            null,
            ADAPTERS,
            new MigrationSettings(false, 0, MigrationMode.ID_DIFF, policy, false))
        .run(folder);
  }

  private static SpecificationSource specsWith(
      Map<tools.vitruv.change.propagation.ConsistencyRuleId, String> hashes,
      Map<tools.vitruv.change.propagation.ConsistencyRuleId, ConsistencyRuleTrigger> triggers) {
    return () -> List.of(SelectiveMockupSpecifications.umlToPcm(hashes, triggers));
  }

  private static Set<String> componentNames(Path folder) {
    Repository repository =
        (Repository) TwoMetamodelMockupMigrationTest.loadRoot(folder.resolve("model.pcm_mockup"));
    return repository.getComponents().stream()
        .map(Component::getName)
        .collect(Collectors.toUnmodifiableSet());
  }

  private MigrationReport migratedWithFallback(Path folder, SpecificationSource newSpecs) {
    MigrationReport report = migrate(folder, newSpecs);
    assertTrue(report.selective().attempted());
    assertTrue(report.selective().fellBackToFull());
    assertTrue(report.migrated(), "the full migration must still have run");
    return report;
  }

  @Test
  @DisplayName("a VSUM without persisted rules falls back to the full migration")
  void test1() throws Exception {
    Path folder = seededVsum(List.of(UmlPcmMockupSpecifications.umlToPcm()));

    MigrationReport report =
        migratedWithFallback(
            folder,
            specsWith(Map.of(CLASS_INSERTED, CLASS_HASH), Map.of(CLASS_INSERTED, CLASS_TRIGGER)));

    assertTrue(
        report.selective().fallbackReason().orElseThrow().contains("no persisted rule hashes"));
  }

  @Test
  @DisplayName("a removed rule without any trigger descriptor falls back")
  void test2() throws Exception {
    Path folder =
        seededVsum(
            List.of(
                SelectiveMockupSpecifications.umlToPcm(
                    Map.of(CLASS_INSERTED, CLASS_HASH, METHOD_INSERTED, METHOD_HASH),
                    Map.of(CLASS_INSERTED, CLASS_TRIGGER))));

    MigrationReport report =
        migratedWithFallback(
            folder,
            specsWith(Map.of(CLASS_INSERTED, CLASS_HASH), Map.of(CLASS_INSERTED, CLASS_TRIGGER)));

    assertTrue(report.selective().fallbackReason().orElseThrow().contains("no trigger descriptor"));
  }

  @Test
  @DisplayName("a trigger referencing an unresolvable metaclass falls back")
  void test3() throws Exception {
    ConsistencyRuleTrigger unresolvable =
        new ConsistencyRuleTrigger(
            "InsertEReference",
            "http://unknown#Nope",
            "methods",
            "",
            ConsistencyRuleTrigger.ValueSlot.NONE);
    Path folder =
        seededVsum(
            List.of(
                SelectiveMockupSpecifications.umlToPcm(
                    Map.of(CLASS_INSERTED, CLASS_HASH, METHOD_INSERTED, METHOD_HASH),
                    Map.of(CLASS_INSERTED, CLASS_TRIGGER, METHOD_INSERTED, unresolvable))));

    MigrationReport report =
        migratedWithFallback(
            folder,
            specsWith(Map.of(CLASS_INSERTED, CLASS_HASH), Map.of(CLASS_INSERTED, CLASS_TRIGGER)));

    assertTrue(report.selective().fallbackReason().orElseThrow().contains("not registered"));
  }

  @Test
  @DisplayName("an affected resource root falls back")
  void test4() throws Exception {
    Path folder =
        seededVsum(
            List.of(
                SelectiveMockupSpecifications.umlToPcm(
                    Map.of(ROOT_INSERTED, ROOT_HASH, CLASS_INSERTED, CLASS_HASH),
                    Map.of(ROOT_INSERTED, ROOT_TRIGGER, CLASS_INSERTED, CLASS_TRIGGER))));

    MigrationReport report =
        migratedWithFallback(
            folder,
            specsWith(
                Map.of(ROOT_INSERTED_V2, ROOT_HASH, CLASS_INSERTED, CLASS_HASH),
                Map.of(ROOT_INSERTED_V2, ROOT_TRIGGER, CLASS_INSERTED, CLASS_TRIGGER)));

    assertTrue(report.selective().fallbackReason().orElseThrow().contains("resource root"));
  }

  @Test
  @DisplayName("a run that falls back to a full migration still preserves hand-written content")
  void test5() throws Exception {
    Path folder = seededVsum(List.of(UmlPcmMockupSpecifications.umlToPcm()));
    addHandWrittenComponent(folder);

    MigrationReport report =
        migrate(
            folder,
            specsWith(Map.of(CLASS_INSERTED, CLASS_HASH), Map.of(CLASS_INSERTED, CLASS_TRIGGER)),
            PreservationPolicy.USER);

    assertTrue(report.selective().fellBackToFull());
    assertTrue(report.preservation().applied());
    assertEquals(
        Set.of("Counter", HAND_WRITTEN_COMPONENT),
        componentNames(folder),
        "falling back means running a full migration, so preservation covers it as usual");
  }

  private void addHandWrittenComponent(Path folder) throws Exception {
    InternalVirtualModel vsum =
        Vsums.build(
            folder, List.of(UmlPcmMockupSpecifications.umlToPcm()), new DetectingUserInteraction());
    try (View view = Vsums.openViewOfAll(vsum, "hand-written-edit")) {
      CommittableView committable = view.withChangeDerivingTrait();
      Repository repository =
          view.getRootObjects().stream()
              .filter(Repository.class::isInstance)
              .map(Repository.class::cast)
              .findFirst()
              .orElseThrow();
      Component handWritten = Pcm_mockupFactory.eINSTANCE.createComponent();
      handWritten.setName(HAND_WRITTEN_COMPONENT);
      repository.getComponents().add(handWritten);
      committable.commitChanges();
    } finally {
      vsum.dispose();
    }
  }

  @SuppressWarnings("UnstableApiUsage")
  private Path seededVsum(List<ChangePropagationSpecification> specifications) throws Exception {
    Path folder = Files.createDirectories(tempDir.resolve("vsum"));
    InternalVirtualModel vsum = Vsums.build(folder, specifications, new DetectingUserInteraction());
    try (View view = Vsums.openEmptyView(vsum, "seed")) {
      CommittableView committable = view.withChangeDerivingTrait();
      UPackage shop = Uml_mockupFactory.eINSTANCE.createUPackage();
      shop.setName("shop");
      UClass counter = Uml_mockupFactory.eINSTANCE.createUClass();
      counter.setName("Counter");
      shop.getClasses().add(counter);
      committable.registerRoot(
          shop, URI.createFileURI(folder.resolve("model.uml_mockup").toString()));
      committable.commitChanges();
    } finally {
      vsum.dispose();
    }

    return folder;
  }
}
