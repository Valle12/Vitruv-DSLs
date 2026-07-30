package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tools.vitruv.dsls.reactions.migration.migration.LibraryCliFixture.assertVsumReloadsWithJarSpecs;
import static tools.vitruv.dsls.reactions.migration.migration.LibraryCliFixture.permissiveInteraction;
import static tools.vitruv.dsls.reactions.migration.migration.LibraryCliFixture.runCli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.applications.util.temporary.java.JavaSetup;
import tools.vitruv.change.propagation.ConsistencyRuleId;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.dsls.reactions.migration.PropagationJars;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;
import tools.vitruv.dsls.reactions.migration.interaction.DetectingUserInteraction;
import tools.vitruv.dsls.reactions.migration.spec.JarSpecificationSource;
import tools.vitruv.dsls.reactions.migration.strategy.ExplicitDominance;
import tools.vitruv.dsls.reactions.migration.vsum.PersistedRules;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

@Slf4j
@ExtendWith(RegisterMetamodelsInStandalone.class)
class Config1ToConfig7MigrationTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();

  private static final ConsistencyRuleId CLASS_AS_CLASS =
      ConsistencyRuleId.of("umlToJavaClassifier::UmlClassInserted");
  private static final ConsistencyRuleId CLASS_AS_INTERFACE =
      ConsistencyRuleId.of("umlToJavaClassifier::UmlClassInsertedAsInterface");
  private static final ConsistencyRuleId DATATYPE_AS_RECORD =
      ConsistencyRuleId.of("umlToJavaClassifier::UmlDataTypeInsertedAsRecord");

  private static final String RULE_DIFF_PHASE = "rule-diff";

  private static final Map<String, String> DERIVED_FROM_SCRATCH = derivedFromScratch();
  private static final Map<String, String> DERIVED_BY_MIGRATION = derivedByMigration();

  @TempDir Path tempDir;

  private static Map<String, String> derivedFromScratch() {
    Map<String, String> expected = new LinkedHashMap<>();
    expected.put("Identifiable", "interface public  | method public getId():int");
    expected.put(
        "Borrowable",
        "interface public  | extends Identifiable | method public borrow(memberId:int):boolean");
    expected.put("MediaType", "enum public  | constants=BOOK,BLU_RAY,MAGAZINE");
    expected.put(
        "Isbn",
        "class public final  | field public code:String | method public getCode():String"
            + " | method public setCode(code:String):void");
    expected.put(
        "Media",
        "interface public  | field public static final title:String"
            + " | field public static final mediaId:int"
            + " | field public static final type:MediaType"
            + " | field public static final weight:double | method public describe():void");
    expected.put(
        "Book",
        "interface public  | field public static final isbn:Isbn"
            + " | field public static final pageCount:int | method public describe():void");
    expected.put("LibraryCard", "interface public  | field public static final cardNumber:String");
    expected.put(
        "Member",
        "interface public  | field public static final name:String"
            + " | field public static final active:boolean"
            + " | field public static final borrowed:ArrayList<Media>"
            + " | method public register(card:LibraryCard):void"
            + " | method public totalWeight():double");
    return expected;
  }

  private static Map<String, String> derivedByMigration() {
    Map<String, String> expected = new LinkedHashMap<>();
    expected.put("Identifiable", "interface public  | method public getId():int");
    expected.put(
        "Borrowable",
        "interface public  | extends Identifiable | method public borrow(memberId:int):boolean");
    expected.put("MediaType", "enum public  | constants=BOOK,BLU_RAY,MAGAZINE");
    expected.put(
        "Isbn",
        "class public final  | field public code:String | method public getCode():String"
            + " | method public setCode(code:String):void");
    expected.put(
        "Media",
        "interface public abstract  | extends Borrowable"
            + " | field public static final title:String"
            + " | field public static final mediaId:int"
            + " | field public static final type:MediaType"
            + " | field public static final weight:double"
            + " | method public describe():void | method public getTitle():String"
            + " | method public setTitle(title:String):void | method public getMediaId():int"
            + " | method public setMediaId(mediaId:int):void | method public getType():MediaType"
            + " | method public setType(type:MediaType):void | method public getWeight():double"
            + " | method public setWeight(weight:double):void");
    expected.put(
        "Book",
        "interface public abstract  | extends Media | field public static final isbn:Isbn"
            + " | field public static final pageCount:int | method public describe():void"
            + " | method public getIsbn():Isbn | method public setIsbn(isbn:Isbn):void"
            + " | method public getPageCount():int"
            + " | method public setPageCount(pageCount:int):void");
    expected.put(
        "LibraryCard",
        "interface public  | field public static final cardNumber:String"
            + " | method public getCardNumber():String"
            + " | method public setCardNumber(cardNumber:String):void");
    expected.put(
        "Member",
        "interface public  | field public static final name:String"
            + " | field public static final active:boolean"
            + " | field public static final borrowed:ArrayList<Media>"
            + " | method public static register(card:LibraryCard):void"
            + " | method public totalWeight():double"
            + " | method public getName():String | method public setName(name:String):void"
            + " | method public getActive():boolean | method public setActive(active:boolean):void"
            + " | method public getBorrowed():ArrayList<Media>"
            + " | method public setBorrowed(borrowed:ArrayList<Media>):void");
    return expected;
  }

  @BeforeAll
  static void prepareStandalone() {
    Vsums.prepareStandalone(ADAPTERS);
  }

  @BeforeEach
  void resetJavaClasspath() {
    JavaSetup.resetClasspathAndRegisterStandardLibrary();
  }

  @Test
  @DisplayName("the fixture, the config1 jar and the config7 jar are the configurations they claim")
  void test1() throws Exception {
    Path vsumFolder = materializedFixture();
    Map<ConsistencyRuleId, String> persisted = PersistedRules.load(vsumFolder).hashes();
    Map<ConsistencyRuleId, String> config1 =
        PropagationJars.ruleHashesOf(PropagationJars.config1());
    Map<ConsistencyRuleId, String> config7 =
        PropagationJars.ruleHashesOf(PropagationJars.config7());

    assertEquals(
        persisted,
        config1,
        "the committed config1 jar and the fixture derived from it have drifted apart;"
            + " rebuild the jar or regenerate the fixture, see the README next to the jars");

    assertTrue(config7.containsKey(CLASS_AS_INTERFACE), config7.keySet().toString());
    assertTrue(config7.containsKey(DATATYPE_AS_RECORD), config7.keySet().toString());
    assertFalse(config7.containsKey(CLASS_AS_CLASS), "config7 must not map classes onto classes");

    SortedSet<ConsistencyRuleId> dirty =
        RuleHashDiff.between(persisted, config7).dirtyIds(MigrationMode.ID_DIFF);
    assertFalse(dirty.isEmpty(), "switching the configuration must make rules dirty");
    log.info(
        "config1 -> config7 dirty rules: {}",
        dirty.stream().map(ConsistencyRuleId::value).toList());
  }

  @Test
  @DisplayName("a selective run completes the tear-down and the migration")
  void test2() throws Exception {
    Path vsumFolder = materializedFixture();

    MigrationReport report = migrateWithConfig7(vsumFolder, MigrationMode.ID_DIFF);

    assertTrue(report.selective().attempted(), "the selective path must be tried first");
    assertFalse(
        report.selective().fellBackToFull(),
        "the tear-down must not give up: " + report.selective().fallbackReason().orElse(""));
    assertTrue(
        report.selective().affectedElementCount() > 0,
        "a configuration switch must repropagate a non-empty set of affected elements");
    assertTrue(report.migrated(), "the repropagation is a migration");
    assertEquals(
        PropagationJars.ruleHashesOf(PropagationJars.config7()),
        PersistedRules.load(vsumFolder).hashes(),
        "the commits must refresh the persisted registry to the rules they ran with");
  }

  @Test
  @DisplayName("a full migration re-derives the library under the config7 rule set")
  void test3() throws Exception {
    Path vsumFolder = materializedFixture();
    Path config7 = PropagationJars.config7();

    assertEquals(
        0, runCli(vsumFolder, config7, "--mode", "full", "--source-update", "none", "--backup"));

    assertEquals(
        PropagationJars.ruleHashesOf(config7),
        PersistedRules.load(vsumFolder).hashes(),
        "the migrated VSUM must record the rules it was migrated with");
    assertTrue(
        Files.exists(vsumFolder.resolve("model").resolve("library.uml")),
        "the dominant source model must survive");
    assertVsumReloadsWithJarSpecs(vsumFolder, config7);
  }

  @Test
  @DisplayName("config7 derives the library from scratch")
  void test4() throws Exception {
    Path vsumFolder = deriveWithConfig7();

    assertTrue(
        Files.exists(vsumFolder.resolve("model").resolve("library.uml")),
        "the UML source the derivation starts from must have been seeded");
    ModelStructure.reportDeviations(
        "the Java model config7 derives from scratch",
        DERIVED_FROM_SCRATCH,
        ModelStructure.ofJava(vsumFolder, ADAPTERS));
  }

  @Test
  @DisplayName("migrating to config7 derives more than building the same model from scratch does")
  void test5() throws Exception {
    Path vsumFolder = materializedFixture();
    assertEquals(
        0,
        runCli(vsumFolder, PropagationJars.config7(), "--mode", "full", "--source-update", "none"));

    Map<String, String> migrated = ModelStructure.ofJava(vsumFolder, ADAPTERS);
    ModelStructure.reportDeviations(
        "the Java model a config7 migration derives", DERIVED_BY_MIGRATION, migrated);
    assertNotEquals(
        DERIVED_FROM_SCRATCH,
        migrated,
        "the two routes now agree - collapse the two baselines into one");
    assertTrue(
        migrated.get(LibraryExampleModel.BOOK).contains("getIsbn")
            && !DERIVED_FROM_SCRATCH.get(LibraryExampleModel.BOOK).contains("getIsbn"),
        "the accessors are the clearest part of what preservation carries over");
  }

  @Test
  @DisplayName("without preservation the migration derives exactly what config7 derives")
  void test7() throws Exception {
    Path vsumFolder = materializedFixture();
    assertEquals(
        0,
        runCli(
            vsumFolder,
            PropagationJars.config7(),
            "--mode",
            "full",
            "--source-update",
            "none",
            "--preserve",
            "off"));

    ModelStructure.reportDeviations(
        "the Java model a config7 migration derives without preservation",
        DERIVED_FROM_SCRATCH,
        ModelStructure.ofJava(vsumFolder, ADAPTERS));
  }

  @Test
  @DisplayName("the UML source survives a forward-only migration to config7")
  void test6() throws Exception {
    Path vsumFolder = materializedFixture();
    Map<String, String> before = ModelStructure.ofUml(vsumFolder, ADAPTERS);
    assertFalse(
        before.get(LibraryExampleModel.MEDIA).contains("static"),
        "the seeded attributes are plain instance attributes: "
            + before.get(LibraryExampleModel.MEDIA));

    migrateWithConfig7(vsumFolder, MigrationMode.FULL);
    ModelStructure.reportDeviations(
        "the UML source after a config7 migration",
        before,
        ModelStructure.ofUml(vsumFolder, ADAPTERS));
  }

  @Test
  @DisplayName("the full mode migrates, and both selective modes complete the same way")
  void test8() throws Exception {
    Path full = freshFixture("mode-full");
    Path ids = freshFixture("mode-ids");
    Path hashes = freshFixture("mode-hashes");

    MigrationReport fullReport = migrateWithConfig7(full, MigrationMode.FULL);

    Map<String, String> fullJava = ModelStructure.ofJava(full, ADAPTERS);
    ModelStructure.reportDeviations(
        "the Java model the full mode derives", DERIVED_BY_MIGRATION, fullJava);
    assertEquals(
        PropagationJars.ruleHashesOf(PropagationJars.config7()),
        PersistedRules.load(full).hashes(),
        "the migrated VSUM must record the rules it was migrated with");

    assertFalse(
        fullReport.selective().attempted(), "the full mode must not try the selective path");
    assertEquals(MigrationMode.FULL, fullReport.selective().mode());
    assertFalse(fullReport.selective().fellBackToFull());
    assertTrue(fullReport.selective().fallbackReason().isEmpty());
    assertFalse(
        fullReport.statistics().phaseNames().contains(RULE_DIFF_PHASE),
        "only a selective run diffs the rule registry: " + fullReport.statistics().phaseNames());
    MigrationReport idsReport = migrateWithConfig7(ids, MigrationMode.ID_DIFF);
    MigrationReport hashesReport = migrateWithConfig7(hashes, MigrationMode.HASH_DIFF);

    Map<String, String> fullUml = ModelStructure.ofUml(full, ADAPTERS);
    for (Path selective : List.of(ids, hashes)) {
      ModelStructure.reportDeviations(
          "the Java model a selective run derives, against the full mode",
          fullJava,
          ModelStructure.ofJava(selective, ADAPTERS));
      ModelStructure.reportDeviations(
          "the UML model a selective run derives, against the full mode",
          fullUml,
          ModelStructure.ofUml(selective, ADAPTERS));
      assertEquals(
          PersistedRules.load(full).hashes(),
          PersistedRules.load(selective).hashes(),
          "every mode must leave the same rule registry behind");
    }

    assertEquals(MigrationMode.ID_DIFF, idsReport.selective().mode());
    assertEquals(MigrationMode.HASH_DIFF, hashesReport.selective().mode());
    for (MigrationReport selective : List.of(idsReport, hashesReport)) {
      assertTrue(selective.selective().attempted());
      assertFalse(
          selective.selective().fellBackToFull(),
          "the tear-down must not give up: " + selective.selective().fallbackReason().orElse(""));
      assertTrue(
          selective.selective().affectedElementCount() > 0,
          "a configuration switch must repropagate a non-empty set of affected elements");
      assertTrue(selective.statistics().phaseNames().contains(RULE_DIFF_PHASE));

      assertTrue(selective.migrated(), "the repropagation is a migration");
      assertEquals(fullReport.sources(), selective.sources());
      assertEquals(fullReport.derived(), selective.derived());
      assertEquals(fullReport.preservation().policy(), selective.preservation().policy());
      assertEquals(fullReport.preservation().applied(), selective.preservation().applied());
    }

    assertTrue(
        fullReport.preservation().lost().stream()
            .anyMatch(
                item ->
                    item.element()
                        .contains(
                            LibraryExampleModel.MEMBER + "." + LibraryUserContent.COMPUTED_METHOD)),
        "config7 has no place for the totalWeight body, and losing it must be reported under"
            + " the method's own name, or nobody can find it: "
            + fullReport.preservation().lost());
  }

  private Path materializedFixture() throws Exception {
    return LibraryVsumFixtureGenerator.materializeInto(tempDir);
  }

  private Path freshFixture(String name) throws Exception {
    return LibraryVsumFixtureGenerator.materializeInto(
        Files.createDirectories(tempDir.resolve(name)));
  }

  private MigrationReport migrateWithConfig7(Path vsumFolder, MigrationMode mode) throws Exception {
    try (JarSpecificationSource specifications =
        JarSpecificationSource.fromJar(PropagationJars.config7())) {
      return new MigrationRunner(
              specifications,
              new PropagationGraph(specifications.createSpecifications()),
              new ExplicitDominance("uml"),
              new TestUserInteraction.ResultProvider(permissiveInteraction()),
              ADAPTERS,
              MigrationSettings.forwardOnly().withMode(mode).withAsking(true))
          .run(vsumFolder);
    }
  }

  private Path deriveWithConfig7() throws Exception {
    Path vsumFolder = Files.createDirectories(tempDir.resolve("fresh-vsum"));
    JavaSetup.resetClasspathAndRegisterStandardLibrary();
    try (JarSpecificationSource specifications =
        JarSpecificationSource.fromJar(PropagationJars.config7())) {
      InternalVirtualModel vsum =
          Vsums.build(
              vsumFolder,
              specifications.createSpecifications(),
              new DetectingUserInteraction(
                  new TestUserInteraction.ResultProvider(permissiveInteraction())));
      try {
        LibraryExampleModel.seedInto(vsum, vsumFolder);
      } finally {
        vsum.dispose();
      }
    }

    return vsumFolder;
  }
}
