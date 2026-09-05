package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import pcm_mockup.PInterface;
import pcm_mockup.PMethod;
import pcm_mockup.Repository;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;
import tools.vitruv.dsls.reactions.migration.preservation.LossReason;
import tools.vitruv.dsls.reactions.migration.preservation.LostItem;
import tools.vitruv.dsls.reactions.migration.preservation.PreservationPolicy;
import tools.vitruv.dsls.reactions.migration.spec.SpecificationSource;
import tools.vitruv.dsls.reactions.migration.strategy.ExplicitDominance;
import tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupWorlds;
import tools.vitruv.dsls.reactions.migration.vsum.PersistedRules;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;

@ExtendWith(RegisterMetamodelsInStandalone.class)
class SelectivePreservationTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();
  private static final String DERIVED_METHOD = PcmMockupFixture.DERIVED_METHOD;
  private static final String HAND_WRITTEN_METHOD = PcmMockupFixture.HAND_WRITTEN_METHOD;
  private static final SpecificationSource SEED_RULES = SelectiveMockupWorlds.forwardVersion1();

  @TempDir Path tempDir;

  @BeforeAll
  static void prepareStandalone() {
    Vsums.prepareStandalone(ADAPTERS);
  }

  private static MigrationReport migrate(
      Path folder, SpecificationSource newSpecifications, PreservationPolicy policy) {
    return migrate(folder, newSpecifications, policy, null);
  }

  private static MigrationReport migrate(
      Path folder,
      SpecificationSource newSpecifications,
      PreservationPolicy policy,
      TestUserInteraction interaction) {
    return new MigrationRunner(
            newSpecifications,
            new PropagationGraph(newSpecifications.createSpecifications()),
            new ExplicitDominance("uml_mockup"),
            interaction == null ? null : new TestUserInteraction.ResultProvider(interaction),
            ADAPTERS,
            new MigrationSettings(false, 0, MigrationMode.ID_DIFF, policy, interaction != null))
        .run(folder);
  }

  private static TestUserInteraction decliningEveryOffer() {
    TestUserInteraction interaction = new TestUserInteraction();
    interaction.onMultipleChoiceSingleSelection(question -> true).always().respondWithChoiceAt(1);
    return interaction;
  }

  private static Repository repository(Path folder) {
    return PcmMockupFixture.repository(folder);
  }

  private static Set<String> methodNamesOfInterface(Path folder) {
    return PcmMockupFixture.methodNamesOfInterface(folder);
  }

  private static boolean lostFor(MigrationReport report) {
    return report.preservation().lost().stream()
        .filter(item -> item.reason() == LossReason.RULE_NO_LONGER_DERIVES)
        .map(LostItem::element)
        .anyMatch(described -> described.contains(SelectivePreservationTest.DERIVED_METHOD));
  }

  @Test
  @DisplayName("hand-written content survives a selective repropagation")
  void test1() throws Exception {
    Path folder = seededVsumWithHandWrittenMethod();

    MigrationReport report =
        migrate(
            folder,
            SelectiveMockupWorlds.forwardWithRenamedInterfaceRule(),
            PreservationPolicy.USER);

    assertTrue(report.migrated());
    assertTrue(report.selective().attempted());
    assertFalse(
        report.selective().fellBackToFull(),
        "this must be the selective path, not a full migration in disguise");
    assertEquals(1, report.selective().affectedElementCount());
    assertTrue(report.preservation().applied());
    assertEquals(
        Set.of(DERIVED_METHOD, HAND_WRITTEN_METHOD),
        methodNamesOfInterface(folder),
        "the new rules rebuild the derived method and preservation brings the other one back");
    assertTrue(report.preservation().lost().isEmpty(), "" + report.preservation().lost());
  }

  @Test
  @DisplayName("without preservation a selective repropagation drops hand-written content")
  void test2() throws Exception {
    Path folder = seededVsumWithHandWrittenMethod();

    MigrationReport report =
        migrate(
            folder,
            SelectiveMockupWorlds.forwardWithRenamedInterfaceRule(),
            PreservationPolicy.OFF);

    assertTrue(report.migrated());
    assertFalse(report.preservation().attempted());
    assertEquals(
        Set.of(DERIVED_METHOD),
        methodNamesOfInterface(folder),
        "the delete-commit took the hand-written method with the interface it lived in");
  }

  @Test
  @DisplayName("the report policy names what a selective run would lose without applying it")
  void test3() throws Exception {
    Path folder = seededVsumWithHandWrittenMethod();

    MigrationReport report =
        migrate(
            folder,
            SelectiveMockupWorlds.forwardWithRenamedInterfaceRule(),
            PreservationPolicy.REPORT);

    assertTrue(report.preservation().attempted());
    assertFalse(report.preservation().applied());
    assertTrue(
        report.preservation().preserved().stream()
            .anyMatch(item -> item.element().contains(HAND_WRITTEN_METHOD)),
        "the analysis names what it would re-attach: " + report.preservation().preserved());
    assertEquals(Set.of(DERIVED_METHOD), methodNamesOfInterface(folder));
  }

  @Test
  @DisplayName("a clean selective run analyses nothing and leaves no recovery folder behind")
  void test4() throws Exception {
    Path folder = seededVsumWithHandWrittenMethod();

    MigrationReport report =
        migrate(folder, SelectiveMockupWorlds.forwardVersion1(), PreservationPolicy.USER);

    assertFalse(report.migrated());
    assertFalse(
        report.preservation().attempted(),
        "nothing was repropagated, so there was nothing to preserve");
    assertEquals(Set.of(DERIVED_METHOD, HAND_WRITTEN_METHOD), methodNamesOfInterface(folder));
    try (var siblings = Files.list(tempDir)) {
      assertTrue(
          siblings.noneMatch(path -> path.getFileName().toString().contains("-premigration-")),
          "a run that changed nothing must not leave a recovery copy behind");
    }
  }

  @Test
  @DisplayName("a preserving selective run reports the snapshot and preservation phases")
  void test5() throws Exception {
    Path folder = seededVsumWithHandWrittenMethod();

    MigrationReport report =
        migrate(
            folder,
            SelectiveMockupWorlds.forwardWithRenamedInterfaceRule(),
            PreservationPolicy.USER);

    assertEquals(
        List.of(
            "rule-diff",
            "load",
            "roots",
            "dominance",
            "classify",
            "left-out",
            "view-open",
            "selection",
            "snapshot",
            "preregister",
            "delete-commit",
            "reinsert-commit",
            "view-close",
            "preserve",
            "refresh"),
        report.statistics().phaseNames());
  }

  @Test
  @DisplayName("a re-attached method keeps its reference to hand-written content that survived")
  void test7() throws Exception {
    Path folder = seededVsumWithHandWrittenMethod();
    linkHandWrittenMethodToHandWrittenInterface(folder);

    MigrationReport report =
        migrate(
            folder,
            SelectiveMockupWorlds.forwardWithRenamedInterfaceRule(),
            PreservationPolicy.USER);

    assertTrue(report.migrated());
    assertFalse(report.selective().fellBackToFull());
    assertTrue(report.preservation().applied());
    Repository repository = repository(folder);
    PInterface notes =
        repository.getInterfaces().stream()
            .filter(candidate -> "Notes".equals(candidate.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("the untouched interface must survive"));
    PMethod helper =
        repository.getInterfaces().stream()
            .flatMap(pInterface -> pInterface.getMethods().stream())
            .filter(method -> HAND_WRITTEN_METHOD.equals(method.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("the hand-written method must be re-attached"));
    assertSame(
        notes,
        helper.getReturnType(),
        "the re-attached method must point at the interface that survived the repropagation");
    assertTrue(report.preservation().lost().isEmpty(), "" + report.preservation().lost());
  }

  @Test
  @DisplayName("what a removed rule stopped deriving is only reported, unless all is asked for")
  void test6() throws Exception {
    Path reported = seededVsumWithHandWrittenMethod("reported");
    Path restored = seededVsumWithHandWrittenMethod("restored");

    MigrationReport user =
        migrate(
            reported,
            SelectiveMockupWorlds.forwardWithoutMethodRule(),
            PreservationPolicy.USER,
            decliningEveryOffer());
    MigrationReport all =
        migrate(
            restored,
            SelectiveMockupWorlds.forwardWithoutMethodRule(),
            PreservationPolicy.ALL,
            decliningEveryOffer());

    assertEquals(
        Set.of(HAND_WRITTEN_METHOD),
        methodNamesOfInterface(reported),
        "the rules own what they used to derive, so only the hand-written method stays");
    assertTrue(lostFor(user), "the dropped method is reported: " + user.preservation().lost());
    assertEquals(
        Set.of(DERIVED_METHOD, HAND_WRITTEN_METHOD),
        methodNamesOfInterface(restored),
        "all means all, in a selective run just as in a full one");
    assertTrue(all.preservation().lost().isEmpty());
  }

  private Path seededVsumWithHandWrittenMethod() throws Exception {
    return seededVsumWithHandWrittenMethod("vsum");
  }

  private Path seededVsumWithHandWrittenMethod(String name) throws Exception {
    Path folder = PcmMockupFixture.seedWithHandWrittenMethod(tempDir.resolve(name), SEED_RULES);
    assertFalse(
        PersistedRules.load(folder).hashes().isEmpty(),
        "the seed must persist a rule registry for a selective run to diff against");
    return folder;
  }

  private void linkHandWrittenMethodToHandWrittenInterface(Path folder) throws Exception {
    PcmMockupFixture.linkHandWrittenMethodToHandWrittenInterface(folder, SEED_RULES);
  }
}
