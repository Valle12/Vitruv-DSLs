package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.applications.util.temporary.java.JavaSetup;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.dsls.reactions.migration.PropagationJars;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;
import tools.vitruv.dsls.reactions.migration.preservation.PreservationPolicy;
import tools.vitruv.dsls.reactions.migration.spec.JarSpecificationSource;
import tools.vitruv.dsls.reactions.migration.strategy.DominanceStrategy;
import tools.vitruv.dsls.reactions.migration.strategy.ExplicitDominance;
import tools.vitruv.dsls.reactions.migration.strategy.FewestChangesDominance;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;

@ExtendWith(RegisterMetamodelsInStandalone.class)
class JavaFirstChangeCountTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();
  private static final int CLASSES_AS_ENUMS = 8;
  private static final int TARGET = 1;

  @TempDir static Path snapshots;
  private static Path baseline;
  @TempDir Path tempDir;

  @BeforeAll
  static void deriveBaseline() throws Exception {
    Vsums.prepareStandalone(ADAPTERS);
    JavaSetup.resetClasspathAndRegisterStandardLibrary();
    baseline = ConfigBaselines.writeSnapshot(CLASSES_AS_ENUMS, snapshots).snapshot();
  }

  @BeforeEach
  void resetClasspath() {
    JavaSetup.resetClasspathAndRegisterStandardLibrary();
  }

  private MigrationReport migrate(DominanceStrategy strategy) throws Exception {
    Path vsum = ConfigBaselines.materialize(baseline, Files.createDirectories(tempDir));
    try (JarSpecificationSource specifications =
        JarSpecificationSource.fromJar(PropagationJars.config(TARGET))) {
      return new MigrationRunner(
              specifications,
              new PropagationGraph(specifications.createSpecifications()),
              strategy,
              new TestUserInteraction.ResultProvider(LibraryCliFixture.permissiveInteraction()),
              ADAPTERS,
              MigrationSettings.forwardOnly()
                  .withMode(MigrationMode.FULL)
                  .withPreservation(PreservationPolicy.REPORT))
          .run(vsum);
    }
  }

  @Test
  @DisplayName("keeping the Java counts the changes of the re-derived UML")
  void test1() throws Exception {
    MigrationReport report = migrate(new ExplicitDominance("java"));

    assertTrue(report.migrated(), "the migration must run");
    assertTrue(report.selection().changeCountMeasured(), "the count must be taken");
    assertTrue(report.selection().changeCount() > 0, "the UML lost realizations at least");
  }

  @Test
  @DisplayName("the fewest changes strategy completes both trials and counts what it adopts")
  void test2() throws Exception {
    MigrationReport report = migrate(new FewestChangesDominance());

    assertTrue(report.migrated(), "the migration must run");
    assertEquals(2, report.selection().trials().size(), "one trial per metamodel");
    assertTrue(
        report.selection().trials().stream().allMatch(SourceSelection.Trial::completed),
        "both trials complete: " + report.selection().trials());
    SourceSelection.Trial winner =
        report.selection().trialOf(report.sources().getFirst()).orElseThrow();
    assertTrue(
        report.selection().trials().stream()
            .allMatch(trial -> trial.changeCount() >= winner.changeCount()),
        "the winner is the candidate with the fewest changes: " + report.selection().trials());
    assertTrue(report.selection().changeCountMeasured(), "the adopted trial is counted");
  }
}
