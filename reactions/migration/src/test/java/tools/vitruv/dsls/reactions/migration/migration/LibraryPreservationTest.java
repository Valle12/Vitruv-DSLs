package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tools.vitruv.dsls.reactions.migration.migration.LibraryCliFixture.permissiveInteraction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.applications.umljava.UmlToJavaChangePropagationSpecification;
import tools.vitruv.applications.util.temporary.java.JavaSetup;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;
import tools.vitruv.dsls.reactions.migration.preservation.PreservationPolicy;
import tools.vitruv.dsls.reactions.migration.spec.SpecificationSource;
import tools.vitruv.dsls.reactions.migration.strategy.ExplicitDominance;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

@ExtendWith(RegisterMetamodelsInStandalone.class)
class LibraryPreservationTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();
  private static final SpecificationSource SPECS =
      () -> List.of(new UmlToJavaChangePropagationSpecification());

  @TempDir Path tempDir;

  private Path vsumFolder;
  private InternalVirtualModel vsum;

  @BeforeAll
  static void prepareStandalone() {
    Vsums.prepareStandalone(ADAPTERS);
  }

  @BeforeEach
  void buildVsum() throws IOException {
    JavaSetup.resetClasspathAndRegisterStandardLibrary();
    vsumFolder = Files.createDirectories(tempDir.resolve("library-vsum"));
    vsum =
        Vsums.build(
            vsumFolder,
            SPECS.createSpecifications(),
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
  @DisplayName("a hand-written method body survives the re-derivation of the Java model")
  void test1() throws Exception {
    seedLibraryModel();
    LibraryUserContent.writeMethodBody(
        vsum, LibraryExampleModel.BOOK, LibraryUserContent.BODIED_METHOD);
    assertEquals(1, describeStatementCount(), "the body must be persisted before migrating");

    MigrationReport report = migrate(PreservationPolicy.USER);

    assertTrue(report.migrated());
    assertTrue(report.preservation().applied(), "the body must be re-attached");
    assertEquals(
        1, describeStatementCount(), "the re-derived method keeps the body no rule could produce");
    assertTrue(
        report.preservation().preserved().stream()
            .anyMatch(
                item ->
                    item.element().contains("Return")
                        && item.element()
                            .contains(
                                LibraryExampleModel.BOOK + "." + LibraryUserContent.BODIED_METHOD)),
        "the report names the re-attached statement and the method holding it: "
            + report.preservation().preserved());
    assertTrue(
        report.preservation().lost().isEmpty(),
        "unchanged rules lose nothing: " + report.preservation().lost());
  }

  @Test
  @DisplayName("without preservation the re-derived method comes back empty")
  void test2() throws Exception {
    seedLibraryModel();
    LibraryUserContent.writeMethodBody(
        vsum, LibraryExampleModel.BOOK, LibraryUserContent.BODIED_METHOD);

    MigrationReport report = migrate(PreservationPolicy.OFF);

    assertTrue(report.migrated());
    assertFalse(report.preservation().attempted());
    assertEquals(0, describeStatementCount());
  }

  @Test
  @DisplayName("a hand-written method no UML operation corresponds to survives")
  void test3() throws Exception {
    seedLibraryModel();
    LibraryUserContent.addHandWrittenMethod(
        vsum, LibraryExampleModel.MEMBER, LibraryUserContent.HAND_WRITTEN_METHOD);
    assertTrue(memberMethodNames().contains(LibraryUserContent.HAND_WRITTEN_METHOD));

    MigrationReport report = migrate(PreservationPolicy.USER);

    assertTrue(report.preservation().applied());
    Set<String> methods = memberMethodNames();
    assertTrue(
        methods.contains(LibraryUserContent.HAND_WRITTEN_METHOD),
        "the hand-written method must be re-attached: " + methods);
    assertTrue(
        methods.contains("register"),
        "and the rules must still derive what the UML operation asks for: " + methods);
  }

  @Test
  @DisplayName("a hand-written Java file no rule produced is restored")
  void test4() throws Exception {
    seedLibraryModel();
    LibraryUserContent.addHandWrittenCompilationUnit(
        vsum, vsumFolder, LibraryUserContent.HAND_WRITTEN_UNIT);
    assertTrue(LibraryUserContent.hasJavaFile(vsumFolder, LibraryUserContent.HAND_WRITTEN_UNIT));

    MigrationReport report = migrate(PreservationPolicy.USER);

    assertTrue(report.preservation().applied());
    assertTrue(
        LibraryUserContent.hasJavaFile(vsumFolder, LibraryUserContent.HAND_WRITTEN_UNIT),
        "the file belongs to no rule, so the re-derivation must not swallow it");
    assertTrue(
        LibraryUserContent.hasJavaFile(vsumFolder, LibraryExampleModel.BOOK),
        "and the derived files must be back as well");
  }

  @Test
  @DisplayName("the report policy names the body it would re-attach without touching the models")
  void test5() throws Exception {
    seedLibraryModel();
    LibraryUserContent.writeMethodBody(
        vsum, LibraryExampleModel.BOOK, LibraryUserContent.BODIED_METHOD);

    MigrationReport report = migrate(PreservationPolicy.REPORT);

    assertTrue(report.preservation().attempted());
    assertFalse(report.preservation().applied());
    assertTrue(
        report.preservation().preserved().stream()
            .anyMatch(
                item ->
                    item.element().contains("Return")
                        && item.element()
                            .contains(
                                LibraryExampleModel.BOOK + "." + LibraryUserContent.BODIED_METHOD)),
        "the analysis names what it would re-attach and where it sits: "
            + report.preservation().preserved());
    assertEquals(0, describeStatementCount(), "but it leaves the models alone");
  }

  @Test
  @DisplayName("the body a derived signature is not a program without survives the re-derivation")
  void test6() throws Exception {
    seedLibraryModel();
    LibraryUserContent.writeTotalWeightBody(vsum);
    assertEquals(
        LibraryUserContent.COMPUTED_METHOD_STATEMENTS,
        totalWeightStatementCount(),
        "the body must be persisted before migrating");

    MigrationReport report = migrate(PreservationPolicy.USER);

    assertTrue(report.preservation().applied());
    assertEquals(
        LibraryUserContent.COMPUTED_METHOD_STATEMENTS,
        totalWeightStatementCount(),
        "the re-derived method keeps the loop no rule could produce");
  }

  @Test
  @DisplayName("without preservation the re-derived totalWeight comes back empty")
  void test7() throws Exception {
    seedLibraryModel();
    LibraryUserContent.writeTotalWeightBody(vsum);

    MigrationReport report = migrate(PreservationPolicy.OFF);

    assertTrue(report.migrated());
    assertFalse(report.preservation().attempted());
    assertEquals(0, totalWeightStatementCount());
  }

  @Test
  @DisplayName("accessors are told apart by their correspondence tag, so nothing is asked about them")
  void test8() throws Exception {
    seedLibraryModel();

    MigrationReport report = migrate(PreservationPolicy.REPORT);

    assertTrue(report.preservation().attempted());
    List<String> accessorQuestions =
        report.preservation().openDecisions().stream()
            .map(decision -> decision.subject() + " " + decision.detail())
            .filter(question -> question.contains("ClassMethod"))
            .toList();
    assertEquals(
        List.of(),
        accessorQuestions,
        "a getter corresponds to its property tagged 'getter' and the setter tagged 'setter';"
            + " the pass must pair them by that tag instead of asking");
  }

  private int describeStatementCount() throws IOException {
    return LibraryUserContent.statementCount(
        vsumFolder, ADAPTERS, LibraryExampleModel.BOOK, LibraryUserContent.BODIED_METHOD);
  }

  private int totalWeightStatementCount() throws IOException {
    return LibraryUserContent.statementCount(
        vsumFolder, ADAPTERS, LibraryExampleModel.MEMBER, LibraryUserContent.COMPUTED_METHOD);
  }

  private Set<String> memberMethodNames() throws IOException {
    return LibraryUserContent.methodNames(vsumFolder, ADAPTERS, LibraryExampleModel.MEMBER);
  }

  private MigrationReport migrate(PreservationPolicy policy) {
    vsum.dispose();
    vsum = null;
    return new MigrationRunner(
            SPECS,
            new PropagationGraph(SPECS.createSpecifications()),
            new ExplicitDominance("uml"),
            new TestUserInteraction.ResultProvider(permissiveInteraction()),
            ADAPTERS,
            MigrationSettings.forwardOnly().withPreservation(policy))
        .run(vsumFolder);
  }

  private void seedLibraryModel() throws Exception {
    LibraryExampleModel.seedInto(vsum, vsumFolder);
  }
}
