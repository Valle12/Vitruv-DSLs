package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import pcm_mockup.PInterface;
import pcm_mockup.PMethod;
import pcm_mockup.Pcm_mockupFactory;
import pcm_mockup.Repository;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;
import tools.vitruv.dsls.reactions.migration.interaction.DetectingUserInteraction;
import tools.vitruv.dsls.reactions.migration.preservation.DecisionItem;
import tools.vitruv.dsls.reactions.migration.preservation.LossReason;
import tools.vitruv.dsls.reactions.migration.preservation.LostItem;
import tools.vitruv.dsls.reactions.migration.preservation.PreservationPolicy;
import tools.vitruv.dsls.reactions.migration.preservation.PreservationReportWriter;
import tools.vitruv.dsls.reactions.migration.spec.SpecificationSource;
import tools.vitruv.dsls.reactions.migration.strategy.ExplicitDominance;
import tools.vitruv.dsls.reactions.migration.testspecs.UmlPcmMockupSpecifications;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

@ExtendWith(RegisterMetamodelsInStandalone.class)
class PreservationMigrationTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();
  private static final SpecificationSource SAME_RULES =
      () -> List.of(UmlPcmMockupSpecifications.umlToPcm());
  private static final SpecificationSource INTERFACES_AS_COMPONENTS =
      () -> List.of(UmlPcmMockupSpecifications.umlToPcmWithInterfacesAsComponents());
  private static final SpecificationSource WITHOUT_METHOD_RULE =
      () -> List.of(UmlPcmMockupSpecifications.umlToPcmWithoutMethods());
  private static final SpecificationSource WITHOUT_INTERFACES =
      () -> List.of(UmlPcmMockupSpecifications.umlToPcmWithoutInterfaces());
  private static final SpecificationSource RENAMED_FILE =
      () -> List.of(UmlPcmMockupSpecifications.umlToPcmInRenamedFile());
  private static final SpecificationSource RENAMED_METHODS =
      () -> List.of(UmlPcmMockupSpecifications.umlToPcmWithRenamedMethods());
  private static final SpecificationSource EXTRA_METHODS =
      () -> List.of(UmlPcmMockupSpecifications.umlToPcmWithExtraMethods());
  private static final SpecificationSource RENAMED_INTERFACES =
      () -> List.of(UmlPcmMockupSpecifications.umlToPcmWithRenamedInterfaces());
  private static final String HAND_WRITTEN_METHOD = PcmMockupFixture.HAND_WRITTEN_METHOD;
  private static final String DERIVED_METHOD = PcmMockupFixture.DERIVED_METHOD;
  private static final String RENAMED_REPOSITORY_FILE = "model-repository.pcm_mockup";

  @TempDir Path tempDir;

  @BeforeAll
  static void prepareStandalone() {
    Vsums.prepareStandalone(ADAPTERS);
  }

  private static MigrationReport migrate(
      Path folder, SpecificationSource specifications, PreservationPolicy policy) {
    return migrate(folder, specifications, policy, null);
  }

  private static MigrationReport migrate(
      Path folder,
      SpecificationSource specifications,
      PreservationPolicy policy,
      TestUserInteraction interaction) {
    return new MigrationRunner(
            specifications,
            new PropagationGraph(specifications.createSpecifications()),
            new ExplicitDominance("uml_mockup"),
            interaction == null ? null : new TestUserInteraction.ResultProvider(interaction),
            ADAPTERS,
            MigrationSettings.forwardOnly()
                .withPreservation(policy)
                .withAsking(interaction != null))
        .run(folder);
  }

  private static TestUserInteraction answering(int choice) {
    TestUserInteraction interaction = new TestUserInteraction();
    interaction
        .onMultipleChoiceSingleSelection(question -> true)
        .always()
        .respondWithChoiceAt(choice);
    return interaction;
  }

  private static Repository repository(Path folder) {
    return PcmMockupFixture.repository(folder);
  }

  private static Repository renamedRepository(Path folder) {
    return PcmMockupFixture.repository(folder, RENAMED_REPOSITORY_FILE);
  }

  private static Set<String> methodNamesOfInterface(Path folder) {
    return PcmMockupFixture.methodNamesOfInterface(folder);
  }

  private static Set<String> methodNamesOf(Repository repository) {
    return PcmMockupFixture.methodNamesOf(repository);
  }

  private static boolean decided(MigrationReport report, DecisionItem.Kind kind, String subject) {
    return report.preservation().decisions().stream()
        .filter(item -> item.kind() == kind)
        .anyMatch(item -> (item.subject() + " " + item.detail()).contains(subject));
  }

  private static boolean lostFor(MigrationReport report, LossReason reason, String element) {
    return report.preservation().lost().stream()
        .filter(item -> item.reason() == reason)
        .map(LostItem::element)
        .anyMatch(described -> described.contains(element));
  }

  private static List<String> describePreserved(MigrationReport report) {
    return report.preservation().preserved().stream()
        .map(item -> item.element() + " -> " + item.target() + " (" + item.feature() + ")")
        .sorted()
        .toList();
  }

  private static List<String> describeLost(MigrationReport report) {
    return report.preservation().lost().stream()
        .map(item -> item.element() + " -> " + item.reason())
        .sorted()
        .toList();
  }

  @Test
  @DisplayName("hand-written content survives a re-derivation with unchanged rules")
  void test1() throws Exception {
    Path folder = seededVsumWithHandWrittenMethod();

    MigrationReport report = migrate(folder, SAME_RULES, PreservationPolicy.USER);

    assertTrue(report.migrated());
    assertTrue(report.preservation().applied());
    assertEquals(
        Set.of(DERIVED_METHOD, HAND_WRITTEN_METHOD),
        methodNamesOfInterface(folder),
        "the derived method is re-derived and the hand-written one re-attached");
    assertTrue(report.preservation().lost().isEmpty(), "nothing had to be given up");
    assertTrue(
        report.preservation().decisions().isEmpty(),
        "unchanged rules leave nothing to decide: " + report.preservation().decisions());
  }

  @Test
  @DisplayName("content survives the new rules persisting the derived model in another file")
  void test7() throws Exception {
    Path folder = seededVsumWithHandWrittenMethod();

    MigrationReport report = migrate(folder, RENAMED_FILE, PreservationPolicy.USER);

    assertTrue(report.preservation().applied());
    assertEquals(
        Set.of(DERIVED_METHOD, HAND_WRITTEN_METHOD),
        methodNamesOf(renamedRepository(folder)),
        "the counterparts of the old file's content say where it went");
    assertTrue(
        report.preservation().lost().isEmpty(),
        "a renamed file loses nothing: " + report.preservation().lost());
    assertTrue(
        decided(report, DecisionItem.Kind.MOVED, RENAMED_REPOSITORY_FILE),
        "the move is reported: " + report.preservation().decisions());
  }

  @Test
  @DisplayName("a rule change that only renames is a decision, not a loss")
  void test8() throws Exception {
    Path folder = seededVsumWithHandWrittenMethod();

    MigrationReport report = migrate(folder, RENAMED_METHODS, PreservationPolicy.USER);

    assertEquals(
        Set.of(DERIVED_METHOD + "Value", HAND_WRITTEN_METHOD),
        methodNamesOfInterface(folder),
        "the renamed method is matched through its source, so the hand-written one still fits");
    assertTrue(
        report.preservation().lost().isEmpty(),
        "the new name is the rules' business: " + report.preservation().lost());
    assertTrue(
        decided(report, DecisionItem.Kind.REPLACED, DERIVED_METHOD + "Value"),
        "the rename is reported: " + report.preservation().decisions());
  }

  @Test
  @DisplayName("a hand-written member follows its renamed container")
  void test12() throws Exception {
    Path folder = seededVsumWithHandWrittenMethod();

    MigrationReport report = migrate(folder, RENAMED_INTERFACES, PreservationPolicy.USER);

    assertTrue(report.preservation().applied());
    List<PInterface> interfaces = repository(folder).getInterfaces();
    assertEquals(1, interfaces.size(), "the new rules still derive exactly one interface");
    PInterface renamed = interfaces.getFirst();
    assertEquals("ApiRenamed", renamed.getName(), "the new rules rename what they derive");
    assertEquals(
        Set.of(DERIVED_METHOD, HAND_WRITTEN_METHOD),
        renamed.getMethods().stream().map(PMethod::getName).collect(Collectors.toUnmodifiableSet()),
        "the renamed interface is matched through its source, so the hand-written method follows");
    assertTrue(
        report.preservation().lost().isEmpty(),
        "a renamed container loses nothing: " + report.preservation().lost());
    assertTrue(
        decided(report, DecisionItem.Kind.REPLACED, "ApiRenamed"),
        "the rename is reported: " + report.preservation().decisions());
  }

  @Test
  @DisplayName("between several possible partners the user decides")
  void test9() throws Exception {
    Path folder = seededVsumWithHandWrittenMethod();

    MigrationReport report = migrate(folder, EXTRA_METHODS, PreservationPolicy.USER, answering(0));

    assertTrue(
        decided(report, DecisionItem.Kind.ANSWERED, "first"),
        "the first offered partner was chosen: " + report.preservation().decisions());
    assertEquals(
        Set.of(DERIVED_METHOD, HAND_WRITTEN_METHOD, "second"),
        methodNamesOfInterface(folder),
        "the old method continues as 'first' and keeps its hand-written name");
    assertTrue(
        decided(report, DecisionItem.Kind.OVERRIDDEN, HAND_WRITTEN_METHOD),
        "the replaced derived name is reported: " + report.preservation().decisions());
    assertTrue(
        report.preservation().lost().isEmpty(),
        "the hand-written value wins over the derived one: " + report.preservation().lost());
  }

  @Test
  @DisplayName("declining every offer keeps the old element as own content")
  void test10() throws Exception {
    Path folder = seededVsumWithHandWrittenMethod();

    MigrationReport report = migrate(folder, EXTRA_METHODS, PreservationPolicy.USER, answering(2));

    assertTrue(report.preservation().applied());
    assertEquals(
        Set.of(DERIVED_METHOD, "first", "second", HAND_WRITTEN_METHOD),
        methodNamesOfInterface(folder),
        "none of the offered partners is it, so it is user content");
    assertTrue(report.preservation().lost().isEmpty(), "" + report.preservation().lost());
  }

  @Test
  @DisplayName("with nobody to ask the ambiguity is reported and nothing is applied")
  void test11() throws Exception {
    Path folder = seededVsumWithHandWrittenMethod();

    MigrationReport report = migrate(folder, EXTRA_METHODS, PreservationPolicy.USER);

    assertTrue(
        lostFor(report, LossReason.AMBIGUOUS_COUNTERPART, HAND_WRITTEN_METHOD),
        "an unanswered guess is not a decision: " + report.preservation().lost());
    assertEquals(
        Set.of(DERIVED_METHOD, "first", "second"),
        methodNamesOfInterface(folder),
        "the old method is neither merged nor duplicated");
    assertTrue(Files.isDirectory(report.preservation().recovery().orElseThrow()));
  }

  @Test
  @DisplayName("hand-written content the new metaclass cannot hold is reported with a backup")
  void test2() throws Exception {
    Path folder = seededVsumWithHandWrittenMethod();

    MigrationReport report = migrate(folder, INTERFACES_AS_COMPONENTS, PreservationPolicy.USER);

    assertTrue(report.migrated());
    assertTrue(repository(folder).getInterfaces().isEmpty(), "the new rules derive no interfaces");
    assertTrue(
        lostFor(report, LossReason.NO_COMPATIBLE_FEATURE, HAND_WRITTEN_METHOD),
        "the hand-written method has no place in a component: " + report.preservation().lost());
    assertTrue(
        lostFor(report, LossReason.RULE_NO_LONGER_DERIVES, DERIVED_METHOD),
        "the rule-produced method is reported as dropped: " + report.preservation().lost());
    Path recovery = report.preservation().recovery().orElseThrow();
    assertTrue(Files.isDirectory(recovery), "the pre-migration state is kept for recovery");
    assertTrue(Files.exists(recovery.resolve(PreservationReportWriter.FILE_NAME)));
    assertTrue(Files.exists(recovery.resolve("model.pcm_mockup")));
  }

  @Test
  @DisplayName("the off policy leaves the migration lossy")
  void test3() throws Exception {
    Path folder = seededVsumWithHandWrittenMethod();

    MigrationReport report = migrate(folder, SAME_RULES, PreservationPolicy.OFF);

    assertFalse(report.preservation().attempted());
    assertEquals(Set.of(DERIVED_METHOD), methodNamesOfInterface(folder));
    assertFalse(
        Files.exists(folder.resolve(PreservationReportWriter.FILE_NAME)),
        "a run that preserves nothing has nothing to report");
  }

  @Test
  @DisplayName("the report lands in the migrated folder, pointing recovery elsewhere")
  void test15() throws Exception {
    Path folder = seededVsumWithHandWrittenMethod();

    MigrationReport report = migrate(folder, INTERFACES_AS_COMPONENTS, PreservationPolicy.USER);

    Path inVsum = folder.resolve(PreservationReportWriter.FILE_NAME);
    assertTrue(Files.exists(inVsum), "what preservation did belongs where the models are");
    String text = Files.readString(inVsum);
    assertTrue(text.contains(HAND_WRITTEN_METHOD), "the manual-check list names the item: " + text);

    Path recovery = report.preservation().recovery().orElseThrow();
    assertTrue(
        text.contains(recovery.toAbsolutePath().toString()),
        "the report in the VSUM folder must name the folder to recover from: " + text);
    assertTrue(
        Files.readString(recovery.resolve(PreservationReportWriter.FILE_NAME))
            .contains("Recover these from the models next to this report."),
        "the copy in the recovery folder keeps the wording that is true there");
  }

  @Test
  @DisplayName("a folder already holding a report can be migrated again")
  void test16() throws Exception {
    Path folder = seededVsumWithHandWrittenMethod();
    migrate(folder, SAME_RULES, PreservationPolicy.USER);
    assertTrue(Files.exists(folder.resolve(PreservationReportWriter.FILE_NAME)));

    MigrationReport second = migrate(folder, SAME_RULES, PreservationPolicy.USER);

    assertTrue(second.migrated());
    assertTrue(second.preservation().attempted());
    assertEquals(
        Set.of(DERIVED_METHOD, HAND_WRITTEN_METHOD),
        methodNamesOfInterface(folder),
        "migrating twice must preserve what migrating once did");
  }

  @Test
  @DisplayName("the report policy analyses without touching the models")
  void test4() throws Exception {
    Path folder = seededVsumWithHandWrittenMethod();

    MigrationReport report = migrate(folder, SAME_RULES, PreservationPolicy.REPORT);

    assertTrue(report.preservation().attempted());
    assertFalse(report.preservation().applied());
    assertTrue(
        report.preservation().preserved().stream()
            .anyMatch(item -> item.element().contains(HAND_WRITTEN_METHOD)),
        "the analysis names what it would re-attach: " + report.preservation().preserved());
    assertEquals(Set.of(DERIVED_METHOD), methodNamesOfInterface(folder));
  }

  @Test
  @DisplayName("what the report says would be kept is what applying actually keeps")
  void test17() throws Exception {
    Path reported = seededVsumWithHandWrittenMethod("reported");
    Path applied = seededVsumWithHandWrittenMethod("applied");

    MigrationReport report = migrate(reported, INTERFACES_AS_COMPONENTS, PreservationPolicy.REPORT);
    MigrationReport user = migrate(applied, INTERFACES_AS_COMPONENTS, PreservationPolicy.USER);

    assertFalse(
        describeLost(user).isEmpty(),
        "these rules can hold none of the old content, so the loss list is what there is to"
            + " compare - without this the two empty lists below would match vacuously");
    assertEquals(
        describePreserved(user),
        describePreserved(report),
        "the analysis must name exactly what applying keeps");
    assertEquals(describeLost(user), describeLost(report), "and exactly what applying cannot keep");
  }

  @Test
  @DisplayName("the report is a faithful preview when there is something to keep, too")
  void test18() throws Exception {
    Path reported = seededVsumWithHandWrittenMethod("reported");
    Path applied = seededVsumWithHandWrittenMethod("applied");

    MigrationReport report = migrate(reported, SAME_RULES, PreservationPolicy.REPORT);
    MigrationReport user = migrate(applied, SAME_RULES, PreservationPolicy.USER);

    assertFalse(
        describePreserved(user).isEmpty(),
        "this rule set does keep the hand-written method, which is the point of the comparison");
    assertEquals(
        describePreserved(user),
        describePreserved(report),
        "the analysis must name exactly what applying keeps");
    assertEquals(describeLost(user), describeLost(report), "and exactly what applying cannot keep");
  }

  @Test
  @DisplayName("what the new rules stopped deriving is only reported, unless all is asked for")
  void test6() throws Exception {
    Path reported = seededVsumWithHandWrittenMethod("reported");
    Path restored = seededVsumWithHandWrittenMethod("restored");

    MigrationReport user = migrate(reported, WITHOUT_METHOD_RULE, PreservationPolicy.USER);
    MigrationReport all = migrate(restored, WITHOUT_METHOD_RULE, PreservationPolicy.ALL);

    assertEquals(Set.of(HAND_WRITTEN_METHOD), methodNamesOfInterface(reported));
    assertTrue(
        lostFor(user, LossReason.RULE_NO_LONGER_DERIVES, DERIVED_METHOD),
        "the rules own what they used to derive: " + user.preservation().lost());
    assertEquals(
        Set.of(DERIVED_METHOD, HAND_WRITTEN_METHOD),
        methodNamesOfInterface(restored),
        "the all policy keeps the dropped content as well");
    assertTrue(all.preservation().lost().isEmpty());
  }

  @Test
  @DisplayName("preserved content may reference other preserved content")
  void test13() throws Exception {
    Path folder = seededVsumWithHandWrittenMethod();
    linkHandWrittenMethodToHandWrittenInterface(folder);

    MigrationReport report = migrate(folder, SAME_RULES, PreservationPolicy.USER);

    assertTrue(report.preservation().applied());
    Repository repository = repository(folder);
    PInterface notes =
        repository.getInterfaces().stream()
            .filter(candidate -> "Notes".equals(candidate.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("the hand-written interface must be back"));
    PMethod helper =
        repository.getInterfaces().stream()
            .flatMap(pInterface -> pInterface.getMethods().stream())
            .filter(method -> HAND_WRITTEN_METHOD.equals(method.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("the hand-written method must be back"));
    assertSame(
        notes,
        helper.getReturnType(),
        "the reference between the two re-attached elements must bind to the re-attached copy");
    assertTrue(
        report.preservation().lost().isEmpty(),
        "content referencing preserved content loses nothing: " + report.preservation().lost());
  }

  @Test
  @DisplayName("a hand-written method keeps its dropped derived interface as a carrier")
  void test14() throws Exception {
    Path folder = seededVsumWithHandWrittenMethod();

    MigrationReport report = migrate(folder, WITHOUT_INTERFACES, PreservationPolicy.USER);

    assertTrue(report.preservation().applied());
    List<PInterface> interfaces = repository(folder).getInterfaces();
    assertEquals(1, interfaces.size(), "the dropped interface must come back as a carrier");
    PInterface carrier = interfaces.getFirst();
    assertEquals("Api", carrier.getName());
    assertEquals(
        Set.of(HAND_WRITTEN_METHOD),
        carrier.getMethods().stream().map(PMethod::getName).collect(Collectors.toSet()),
        "the carrier keeps the hand-written method; the derived one is the rules' business");
    assertTrue(
        decided(report, DecisionItem.Kind.CARRIER, "Api"),
        "the carrier is reported: " + report.preservation().decisions());
    assertTrue(
        report.preservation().lost().isEmpty(),
        "the carrier keeps the user content out of the losses: " + report.preservation().lost());
  }

  @Test
  @DisplayName("a hand-written model file no rule produced is restored")
  void test5() throws Exception {
    Path folder = seededVsumWithHandWrittenMethod();
    addHandWrittenModelFile(folder);

    MigrationReport report = migrate(folder, SAME_RULES, PreservationPolicy.USER);

    assertTrue(report.preservation().applied());
    assertTrue(
        Files.exists(folder.resolve("notes.pcm_mockup")),
        "the file belongs to no rule, so the re-derivation must not swallow it");
    Repository notes =
        (Repository) TwoMetamodelMockupMigrationTest.loadRoot(folder.resolve("notes.pcm_mockup"));
    assertEquals("notes", notes.getName());
  }

  @SuppressWarnings("UnstableApiUsage")
  private void addHandWrittenModelFile(Path folder) throws Exception {
    InternalVirtualModel vsum =
        Vsums.build(folder, SAME_RULES.createSpecifications(), new DetectingUserInteraction());
    try (View view = Vsums.openViewOfAll(vsum, "hand-written-file")) {
      CommittableView committable = view.withChangeDerivingTrait();
      Repository notes = Pcm_mockupFactory.eINSTANCE.createRepository();
      notes.setName("notes");
      committable.registerRoot(
          notes, URI.createFileURI(folder.resolve("notes.pcm_mockup").toString()));
      committable.commitChanges();
    } finally {
      vsum.dispose();
    }
  }

  private Path seededVsumWithHandWrittenMethod() throws Exception {
    return seededVsumWithHandWrittenMethod("vsum");
  }

  private Path seededVsumWithHandWrittenMethod(String name) throws Exception {
    return PcmMockupFixture.seedWithHandWrittenMethod(tempDir.resolve(name), SAME_RULES);
  }

  private void linkHandWrittenMethodToHandWrittenInterface(Path folder) throws Exception {
    PcmMockupFixture.linkHandWrittenMethodToHandWrittenInterface(folder, SAME_RULES);
  }
}
