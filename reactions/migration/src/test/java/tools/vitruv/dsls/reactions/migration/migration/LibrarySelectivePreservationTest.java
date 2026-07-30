package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tools.vitruv.dsls.reactions.migration.migration.LibraryCliFixture.permissiveInteraction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.eclipse.uml2.uml.UMLPackage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.applications.umljava.UmlToJavaChangePropagationSpecification;
import tools.vitruv.applications.util.temporary.java.JavaSetup;
import tools.vitruv.change.propagation.ConsistencyRuleId;
import tools.vitruv.change.propagation.ConsistencyRuleTrigger;
import tools.vitruv.change.propagation.ConsistencyRuleTrigger.ValueSlot;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;
import tools.vitruv.dsls.reactions.migration.preservation.PreservationPolicy;
import tools.vitruv.dsls.reactions.migration.spec.SpecificationSource;
import tools.vitruv.dsls.reactions.migration.strategy.ExplicitDominance;
import tools.vitruv.dsls.reactions.migration.testspecs.RuleAwareSpecification;
import tools.vitruv.dsls.reactions.migration.vsum.PersistedRules;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

@ExtendWith(RegisterMetamodelsInStandalone.class)
class LibrarySelectivePreservationTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();
  private static final String UML = UMLPackage.eNS_URI;

  private static final ConsistencyRuleId OPERATION_RULE =
      ConsistencyRuleId.of("umljava::OperationInserted");
  private static final ConsistencyRuleId CLASS_RULE =
      ConsistencyRuleId.of("umljava::ClassInserted");
  private static final String OPERATION_HASH_V1 = "a1".repeat(32);
  private static final String OPERATION_HASH_V2 = "a2".repeat(32);
  private static final String CLASS_HASH_V1 = "b1".repeat(32);
  private static final String CLASS_HASH_V2 = "b2".repeat(32);

  private static final ConsistencyRuleTrigger OPERATION_TRIGGER =
      new ConsistencyRuleTrigger(
          "InsertEReference",
          UML + "#Class",
          "ownedOperation",
          UML + "#Operation",
          ValueSlot.NEW_VALUE);
  private static final ConsistencyRuleTrigger CLASS_TRIGGER =
      new ConsistencyRuleTrigger(
          "InsertEReference",
          UML + "#Package",
          "packagedElement",
          UML + "#Class",
          ValueSlot.NEW_VALUE);

  @TempDir Path tempDir;

  private Path vsumFolder;
  private InternalVirtualModel vsum;

  @BeforeAll
  static void prepareStandalone() {
    Vsums.prepareStandalone(ADAPTERS);
  }

  private static SpecificationSource specsWith(String operationHash, String classHash) {
    return () ->
        List.of(
            new RuleAwareSpecification(
                new UmlToJavaChangePropagationSpecification(),
                Map.of(OPERATION_RULE, operationHash, CLASS_RULE, classHash),
                Map.of(OPERATION_RULE, OPERATION_TRIGGER, CLASS_RULE, CLASS_TRIGGER)));
  }

  private static SpecificationSource version1() {
    return specsWith(OPERATION_HASH_V1, CLASS_HASH_V1);
  }

  private static SpecificationSource withEditedOperationRule() {
    return specsWith(OPERATION_HASH_V2, CLASS_HASH_V1);
  }

  private static SpecificationSource withEditedClassRule() {
    return specsWith(OPERATION_HASH_V1, CLASS_HASH_V2);
  }

  @BeforeEach
  void buildVsum() throws IOException {
    JavaSetup.resetClasspathAndRegisterStandardLibrary();
    vsumFolder = Files.createDirectories(tempDir.resolve("library-vsum"));
    vsum =
        Vsums.build(
            vsumFolder,
            version1().createSpecifications(),
            new TestUserInteraction.ResultProvider(permissiveInteraction()));
  }

  @AfterEach
  void disposeVsum() {
    if (vsum != null) {
      vsum.dispose();
      vsum = null;
    }
  }

  @Test
  @DisplayName("a hand-written method body survives a selective repropagation")
  void test1() throws Exception {
    seedLibraryModelWithMethodBody();

    MigrationReport report = migrate(withEditedOperationRule(), PreservationPolicy.USER);

    assertTrue(report.migrated());
    assertFalse(
        report.selective().fellBackToFull(),
        "this must be the selective path, not a full migration in disguise: "
            + report.selective().fallbackReason());
    assertEquals(1, report.selective().dirtyRuleCount());
    assertTrue(report.preservation().applied());
    assertEquals(
        1,
        describeStatementCount(),
        "the repropagated method keeps the body no rule could produce");
  }

  @Test
  @DisplayName("without preservation a selective repropagation empties the method body")
  void test2() throws Exception {
    seedLibraryModelWithMethodBody();

    MigrationReport report = migrate(withEditedOperationRule(), PreservationPolicy.OFF);

    assertTrue(report.migrated());
    assertFalse(report.selective().fellBackToFull());
    assertFalse(report.preservation().attempted());
    assertEquals(
        0, describeStatementCount(), "the delete-commit took the body with the method it was in");
  }

  @Test
  @DisplayName("a clean selective run of the library repropagates nothing")
  void test3() throws Exception {
    seedLibraryModelWithMethodBody();

    MigrationReport report = migrate(version1(), PreservationPolicy.USER);

    assertFalse(report.migrated());
    assertEquals(0, report.selective().dirtyRuleCount());
    assertFalse(report.preservation().attempted());
    assertEquals(1, describeStatementCount(), "and leaves the hand-written body alone");
  }

  @Test
  @DisplayName("a hand-written method body survives a class-level selective repropagation")
  void test4() throws Exception {
    seedLibraryModelWithMethodBody();

    MigrationReport report = migrate(withEditedClassRule(), PreservationPolicy.USER);

    assertTrue(report.migrated());
    assertFalse(
        report.selective().fellBackToFull(),
        "the class granularity must work selectively: " + report.selective().fallbackReason());
    assertEquals(1, report.selective().dirtyRuleCount());
    assertEquals(
        4,
        report.selective().affectedElementCount(),
        "the four classes of the library must be repropagated");
    assertTrue(report.preservation().applied());
    assertEquals(
        1,
        describeStatementCount(),
        "the repropagated classifier keeps the body no rule could produce");
  }

  @Test
  @DisplayName("a class-level selective repropagation re-derives the torn-down classifiers")
  void test5() throws Exception {
    seedLibraryModelWithMethodBody();

    MigrationReport report = migrate(withEditedClassRule(), PreservationPolicy.OFF);

    assertTrue(report.migrated());
    assertFalse(report.selective().fellBackToFull());
    assertTrue(
        LibraryUserContent.hasJavaFile(vsumFolder, LibraryExampleModel.BOOK),
        "the new rules must re-derive the torn-down classifier");
    assertEquals(
        0, describeStatementCount(), "the delete-commit took the body with the classifier");
  }

  private int describeStatementCount() throws IOException {
    return LibraryUserContent.statementCount(
        vsumFolder, ADAPTERS, LibraryExampleModel.BOOK, LibraryUserContent.BODIED_METHOD);
  }

  private MigrationReport migrate(
      SpecificationSource newSpecifications, PreservationPolicy policy) {
    vsum.dispose();
    vsum = null;
    return new MigrationRunner(
            newSpecifications,
            new PropagationGraph(newSpecifications.createSpecifications()),
            new ExplicitDominance("uml"),
            new TestUserInteraction.ResultProvider(permissiveInteraction()),
            ADAPTERS,
            new MigrationSettings(false, 0, MigrationMode.HASH_DIFF, policy, false))
        .run(vsumFolder);
  }

  private void seedLibraryModelWithMethodBody() throws Exception {
    seedLibraryModel();
    LibraryUserContent.writeMethodBody(
        vsum, LibraryExampleModel.BOOK, LibraryUserContent.BODIED_METHOD);
    assertEquals(1, describeStatementCount(), "the body must be persisted before migrating");
  }

  private void seedLibraryModel() throws Exception {
    LibraryExampleModel.seedInto(vsum, vsumFolder);
    assertFalse(
        PersistedRules.load(vsumFolder).hashes().isEmpty(),
        "the seed must persist a rule registry for a selective run to diff against");
  }
}
