package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tools.vitruv.dsls.reactions.migration.migration.LibraryCliFixture.assertVsumReloadsWithJarSpecs;
import static tools.vitruv.dsls.reactions.migration.migration.LibraryCliFixture.permissiveInteraction;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.vitruv.applications.util.temporary.java.JavaSetup;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.dsls.reactions.migration.PropagationJars;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;
import tools.vitruv.dsls.reactions.migration.preservation.PreservationPolicy;
import tools.vitruv.dsls.reactions.migration.spec.JarSpecificationSource;
import tools.vitruv.dsls.reactions.migration.strategy.ExplicitDominance;
import tools.vitruv.dsls.reactions.migration.vsum.PersistedRules;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;

@Slf4j
@Tag("matrix")
@ExtendWith(RegisterMetamodelsInStandalone.class)
abstract class ConfigMatrixCells {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();

  @TempDir Path tempDir;

  private Path sourceSnapshot;

  @BeforeAll
  static void prepareStandalone() {
    Vsums.prepareStandalone(ADAPTERS);
  }

  private static Map<String, String> filesOf(Path vsumFolder) throws Exception {
    Map<String, String> files = new LinkedHashMap<>();
    for (String folder : List.of("model", "src")) {
      String extension = "model".equals(folder) ? ".uml" : ".java";
      Path root = vsumFolder.resolve(folder);
      for (Path file : ModelStructure.modelFiles(root, extension)) {
        files.put(root.relativize(file).toString().replace('\\', '/'), Files.readString(file));
      }
    }

    return files;
  }

  protected abstract int sourceConfiguration();

  @BeforeEach
  void resetClasspath() {
    JavaSetup.resetClasspathAndRegisterStandardLibrary();
  }

  @ParameterizedTest(name = "to config{0}")
  @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9})
  void migratesTo(int target) throws Exception {
    Path vsumFolder = materializedSource();
    Map<String, String> before = filesOf(vsumFolder);

    MigrationReport report = migrateTo(vsumFolder, target);

    assertFalse(
        report.selective().fellBackToFull(),
        "the selective migration must not give up: "
            + report.selective().fallbackReason().orElse(""));
    assertTrue(report.selective().attempted(), "--mode ids always attempts the selective path");
    assertEquals(MigrationMode.ID_DIFF, report.selective().mode());

    if (target == sourceConfiguration()) {
      assertMigratingToItselfChangesNothing(vsumFolder, report, before);
      return;
    }

    assertTrue(
        report.selective().dirtyRuleCount() > 0,
        "the two configurations declare different rules, so some rule must be dirty");
    assertTrue(report.migrated(), "a configuration switch is a migration");
    assertEquals(
        PropagationJars.ruleHashesOf(PropagationJars.config(target)),
        PersistedRules.load(vsumFolder).hashes(),
        "the migrated VSUM must record the rules it was migrated with");
    assertVsumReloadsWithJarSpecs(vsumFolder, PropagationJars.config(target));

    Path fromScratch = deriveFromScratch(target);
    report(
        "Java",
        target,
        ModelStructure.compare(
            ModelStructure.ofJava(fromScratch, ADAPTERS),
            ModelStructure.ofJava(vsumFolder, ADAPTERS)));
    report(
        "UML",
        target,
        ModelStructure.compare(
            ModelStructure.ofUml(fromScratch, ADAPTERS),
            ModelStructure.ofUml(vsumFolder, ADAPTERS)));
  }

  private void report(String kind, int target, ModelStructure.Comparison comparison) {
    log.info(
        "config{} -> config{}, {}: {} deviation(s) from the from-scratch derivation, {}"
            + " ordering-only",
        sourceConfiguration(),
        target,
        kind,
        comparison.content().size(),
        comparison.ordering().size());
    comparison.content().forEach(deviation -> log.info("  {}", deviation));
  }

  private void assertMigratingToItselfChangesNothing(
      Path vsumFolder, MigrationReport report, Map<String, String> before) throws Exception {
    assertEquals(
        0, report.selective().dirtyRuleCount(), "the same configuration cannot have a dirty rule");
    assertEquals(0, report.selective().affectedElementCount());
    assertFalse(report.migrated(), "there was nothing to migrate");
    assertEquals(
        before,
        filesOf(vsumFolder),
        "migrating to the configuration the models were derived with must leave them alone");
  }

  private Path materializedSource() throws Exception {
    if (sourceSnapshot == null) {
      sourceSnapshot =
          ConfigBaselines.writeSnapshot(
                  sourceConfiguration(), Files.createDirectories(tempDir.resolve("baselines")))
              .snapshot();
    }

    return ConfigBaselines.materialize(
        sourceSnapshot, Files.createTempDirectory(tempDir, "migrate"));
  }

  private Path deriveFromScratch(int configuration) throws Exception {
    return ConfigBaselines.materialize(
        ConfigBaselines.writeSnapshot(
                configuration, Files.createTempDirectory(tempDir, "from-scratch"))
            .snapshot(),
        Files.createTempDirectory(tempDir, "from-scratch-vsum"));
  }

  private MigrationReport migrateTo(Path vsumFolder, int target) throws Exception {
    try (JarSpecificationSource specifications =
        JarSpecificationSource.fromJar(PropagationJars.config(target))) {
      return new MigrationRunner(
              specifications,
              new PropagationGraph(specifications.createSpecifications()),
              new ExplicitDominance("uml"),
              new TestUserInteraction.ResultProvider(permissiveInteraction()),
              ADAPTERS,
              MigrationSettings.forwardOnly()
                  .withMode(MigrationMode.ID_DIFF)
                  .withPreservation(PreservationPolicy.REPORT))
          .run(vsumFolder);
    }
  }
}
