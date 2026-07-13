package tools.vitruv.dsls.reactions.migration;

import java.io.IOException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import tools.vitruv.change.interaction.CliInteractionResultProviderImpl;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.cli.CommandLine;
import tools.vitruv.dsls.reactions.migration.cli.MigrationOptions;
import tools.vitruv.dsls.reactions.migration.cli.SourceUpdateMode;
import tools.vitruv.dsls.reactions.migration.cli.UsageException;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;
import tools.vitruv.dsls.reactions.migration.migration.MigrationReport;
import tools.vitruv.dsls.reactions.migration.migration.MigrationRunner;
import tools.vitruv.dsls.reactions.migration.migration.MigrationSettings;
import tools.vitruv.dsls.reactions.migration.spec.JarSpecificationSource;
import tools.vitruv.dsls.reactions.migration.spec.SpecificationSource;
import tools.vitruv.dsls.reactions.migration.strategy.DominanceStrategy;
import tools.vitruv.dsls.reactions.migration.strategy.ExplicitDominance;
import tools.vitruv.dsls.reactions.migration.strategy.FewestChangesDominance;
import tools.vitruv.dsls.reactions.migration.strategy.MaxReachabilityDominance;
import tools.vitruv.dsls.reactions.migration.vsum.VsumBackup;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;

@Slf4j
public class Main {
  public static void main(String[] args) {
    System.exit(run(args));
  }

  public static int run(String[] args) {
    CommandLine commandLine = new CommandLine(args);
    if (commandLine.isHelpRequested()) {
      log.info("\n{}", CommandLine.usage());
      return 0;
    }

    MigrationOptions options;
    try {
      options = MigrationOptions.parse(commandLine);
    } catch (UsageException e) {
      log.error(e.getMessage());
      log.info("\n{}", CommandLine.usage());
      return 1;
    }

    AdapterRegistry adapters = new AdapterRegistry();
    Vsums.prepareStandalone(adapters);

    SpecificationSource specifications;
    try {
      specifications = JarSpecificationSource.fromJar(options.specificationsJar());
    } catch (IOException | RuntimeException e) {
      log.error(
          "Failed to load change-propagation specifications from '{}': {}",
          options.specificationsJar(),
          e.getMessage());
      return 1;
    }

    PropagationGraph graph = new PropagationGraph(specifications.createSpecifications());
    log.info("{}", graph);

    Optional<VsumBackup> backup;
    try {
      backup = createBackupIfRequested(options);
    } catch (IOException e) {
      log.error("Failed to back up the VSUM: {}", e.getMessage());
      return 1;
    }

    MigrationRunner runner =
        new MigrationRunner(
            specifications,
            graph,
            dominanceStrategyFor(options),
            new CliInteractionResultProviderImpl(),
            adapters,
            settingsFor(options));
    log.info("Migrating VSUM at {}", options.vsumFolder().toAbsolutePath());
    try {
      logReport(runner.run(options.vsumFolder()));
      return 0;
    } catch (RuntimeException e) {
      log.error(
          "Migration failed for {}: {}", options.vsumFolder().toAbsolutePath(), e.getMessage(), e);
      backup.ifPresentOrElse(Main::restore, Main::warnRestoreImpossible);
      return 1;
    }
  }

  private static Optional<VsumBackup> createBackupIfRequested(MigrationOptions options)
      throws IOException {
    return options.backup()
        ? Optional.of(VsumBackup.create(options.vsumFolder()))
        : Optional.empty();
  }

  private static DominanceStrategy dominanceStrategyFor(MigrationOptions options) {
    return switch (options.strategy()) {
      case EXPLICIT -> new ExplicitDominance(options.dominantToken().orElseThrow());
      case REACHABILITY -> new MaxReachabilityDominance();
      case FEWEST_CHANGES -> new FewestChangesDominance();
    };
  }

  private static MigrationSettings settingsFor(MigrationOptions options) {
    return new MigrationSettings(
        options.sourceUpdate() == SourceUpdateMode.FIXPOINT, options.maxSourceUpdateRounds());
  }

  private static void logReport(MigrationReport report) {
    if (!report.migrated()) {
      log.info("Nothing to migrate.");
      return;
    }

    log.info(
        "Migration complete. Sources kept: {}; re-derived: {}",
        report.sources().stream().map(MetamodelNode::shortName).toList(),
        report.derived().stream().map(MetamodelNode::shortName).toList());
    if (report.sourceUpdate().attempted()) {
      log.info(
          "Source update: {} round(s) applied, converged={}, remaining delta={}",
          report.sourceUpdate().rounds(),
          report.sourceUpdate().converged(),
          report.sourceUpdate().lastDeltaSize());
    }
    if (!report.requestedInteractions().isEmpty()) {
      log.info("Reactions requested user interaction: {}", report.requestedInteractions());
    }
  }

  private static void restore(VsumBackup backup) {
    try {
      backup.restore();
    } catch (IOException e) {
      log.error("Restoring the backup from {} failed: {}", backup.location(), e.getMessage());
    }
  }

  private static void warnRestoreImpossible() {
    log.warn("No backup was taken (--backup enables automatic restore on failure).");
  }
}
