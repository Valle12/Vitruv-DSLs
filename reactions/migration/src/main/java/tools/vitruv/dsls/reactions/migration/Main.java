package tools.vitruv.dsls.reactions.migration;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import tools.vitruv.change.interaction.CliInteractionResultProviderImpl;
import tools.vitruv.change.interaction.InteractionResultProvider;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.cli.AskMode;
import tools.vitruv.dsls.reactions.migration.cli.CommandLine;
import tools.vitruv.dsls.reactions.migration.cli.MigrationOptions;
import tools.vitruv.dsls.reactions.migration.cli.SourceUpdateMode;
import tools.vitruv.dsls.reactions.migration.cli.UsageException;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;
import tools.vitruv.dsls.reactions.migration.interaction.NonInteractiveDefaults;
import tools.vitruv.dsls.reactions.migration.migration.MigrationFailure;
import tools.vitruv.dsls.reactions.migration.migration.MigrationMode;
import tools.vitruv.dsls.reactions.migration.migration.MigrationReport;
import tools.vitruv.dsls.reactions.migration.migration.MigrationRunner;
import tools.vitruv.dsls.reactions.migration.migration.MigrationSettings;
import tools.vitruv.dsls.reactions.migration.migration.MigrationStatistics;
import tools.vitruv.dsls.reactions.migration.migration.SelectiveOutcome;
import tools.vitruv.dsls.reactions.migration.migration.SourceSelection;
import tools.vitruv.dsls.reactions.migration.preservation.PreservationOutcome;
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
    return run(
        args,
        System.console() == null
            ? new NonInteractiveDefaults()
            : new CliInteractionResultProviderImpl());
  }

  private static void logUsage() {
    log.info("\n{}", CommandLine.USAGE);
  }

  public static int run(String[] args, InteractionResultProvider interactionFallback) {
    CommandLine commandLine = new CommandLine(args);
    if (commandLine.isHelpRequested()) {
      logUsage();
      return 0;
    }

    MigrationOptions options;
    try {
      options = MigrationOptions.parse(commandLine);
    } catch (UsageException e) {
      log.error(e.getMessage());
      logUsage();
      return 1;
    }

    AdapterRegistry adapters = new AdapterRegistry();
    Vsums.prepareStandalone(adapters);

    JarSpecificationSource specifications;
    try {
      specifications = JarSpecificationSource.fromJar(options.specificationsJar());
    } catch (IOException | RuntimeException e) {
      log.error(
          "Failed to load change-propagation specifications from '{}': {}",
          options.specificationsJar(),
          e.getMessage());
      return 1;
    }

    try (specifications) {
      return migrate(options, adapters, specifications, interactionFallback);
    } catch (IOException e) {
      log.error("Failed to release the specification jar: {}", e.getMessage());
      return 1;
    }
  }

  private static int migrate(
      MigrationOptions options,
      AdapterRegistry adapters,
      SpecificationSource specifications,
      InteractionResultProvider interactionFallback) {
    long graphStartedAt = System.nanoTime();
    PropagationGraph graph = new PropagationGraph(specifications.createSpecifications());
    log.info("{}", graph);
    log.info(
        "Built the propagation graph in {} ms",
        millis(Duration.ofNanos(System.nanoTime() - graphStartedAt)));

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
            interactionFallback,
            adapters,
            settingsFor(options, interactionFallback));
    log.info("Migrating VSUM at {}", options.vsumFolder().toAbsolutePath());
    try {
      logReport(runner.run(options.vsumFolder()));
      return 0;
    } catch (MigrationFailure e) {
      log.error(
          "Migration failed for {}: {}", options.vsumFolder().toAbsolutePath(), e.getMessage(), e);
      logSelection(e.selection());
      backup.ifPresentOrElse(Main::restore, Main::warnRestoreImpossible);
      return 1;
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

  private static MigrationSettings settingsFor(
      MigrationOptions options, InteractionResultProvider interactionFallback) {
    if (options.mode() != MigrationMode.FULL) {
      log.info(
          "Selective mode {} repropagates only the affected elements; the source update applies"
              + " only if the run falls back to a full migration.",
          options.mode());
    }

    return new MigrationSettings(
        options.sourceUpdate() == SourceUpdateMode.FIXPOINT,
        options.maxSourceUpdateRounds(),
        options.mode(),
        options.preservation(),
        asksOnAmbiguity(options, interactionFallback));
  }

  private static boolean asksOnAmbiguity(
      MigrationOptions options, InteractionResultProvider interactionFallback) {
    if (options.ask() == AskMode.NEVER || options.preservation().leavesModelsUnchanged()) {
      return false;
    }

    if (interactionFallback instanceof NonInteractiveDefaults) {
      log.info(
          "No interactive console; content the metamodels leave several places for is reported"
              + " instead of asked about.");
      return false;
    }

    return true;
  }

  private static void logReport(MigrationReport report) {
    logSelectiveOutcome(report.selective());
    logStatistics(report.statistics());
    if (!report.migrated()) {
      log.info("Nothing to migrate.");
      return;
    }

    log.info(
        "Migration complete. Sources kept: {}; re-derived: {}",
        report.sources().stream().map(MetamodelNode::shortName).toList(),
        report.derived().stream().map(MetamodelNode::shortName).toList());
    logSelection(report.selection());
    if (report.sourceUpdate().attempted()) {
      log.info(
          "Source update: {} round(s) applied, converged={}, remaining delta={}",
          report.sourceUpdate().rounds(),
          report.sourceUpdate().converged(),
          report.sourceUpdate().lastDeltaSize());
      logRetractions(report.sourceUpdate().retractions());
    }
    logPreservation(report.preservation());
    if (!report.requestedInteractions().isEmpty()) {
      log.info("Reactions requested user interaction: {}", report.requestedInteractions());
    }
  }

  private static void logRetractions(List<String> retractions) {
    if (retractions.isEmpty()) {
      return;
    }

    log.warn(
        "Source update removed {} element(s) the new rules no longer produce:", retractions.size());
    retractions.forEach(retraction -> log.warn("  retracted: {}", retraction));
  }

  private static void logPreservation(PreservationOutcome preservation) {
    if (!preservation.attempted()) {
      return;
    }

    if (preservation.failed()) {
      log.warn(
          "Preservation ({}) did not complete; the migrated models are as the rules derived them:"
              + " {}",
          preservation.policy(),
          preservation.failure());
      return;
    }

    log.info(
        "Preservation ({}): {} item(s) {}, {} item(s) could not be kept, {} left to deal with"
            + " by hand",
        preservation.policy(),
        preservation.preserved().size(),
        preservation.applied() ? "re-attached" : "would be re-attached",
        preservation.lost().size(),
        preservation.manualItems());
    preservation
        .lost()
        .forEach(
            item ->
                log.info("  not kept: {} - {}", item.element(), item.reason().getDescription()));
    preservation
        .decisions()
        .forEach(
            item ->
                log.info(
                    "  {}: {} - {}",
                    item.kind().getDescription(),
                    item.subject(),
                    item.detail() == null ? "" : item.detail()));
    preservation
        .recovery()
        .ifPresent(folder -> log.info("  recover them from {}", folder.toAbsolutePath()));
  }

  private static void logSelection(SourceSelection selection) {
    for (SourceSelection.Trial trial : selection.trials()) {
      if (trial.completed()) {
        log.info(
            "  trial with {} as source: {} proposed change(s) in {} ms",
            trial.candidate().shortName(),
            trial.changeCount(),
            millis(trial.duration()));
      } else {
        log.info(
            "  trial with {} as source did not complete after {} ms: {}",
            trial.candidate().shortName(),
            millis(trial.duration()),
            trial.failure().orElse(""));
      }
    }

    if (selection.changeCountMeasured()) {
      log.info(
          "The re-derivation changed the derived models in {} place(s)", selection.changeCount());
    }
  }

  private static void logStatistics(MigrationStatistics statistics) {
    log.info("Migration took {} ms in total", millis(statistics.total()));
    statistics
        .phases()
        .forEach(phase -> log.info("  {}: {} ms", phase.phase(), millis(phase.duration())));
  }

  private static String millis(Duration duration) {
    return String.format(Locale.ROOT, "%.3f", duration.toNanos() / 1_000_000.0);
  }

  private static void logSelectiveOutcome(SelectiveOutcome selective) {
    if (!selective.attempted()) {
      return;
    }

    if (selective.fellBackToFull()) {
      log.info(
          "Selective mode {} fell back to a full migration: {}",
          selective.mode(),
          selective.fallbackReason().orElse(""));
    } else {
      log.info(
          "Selective mode {}: {} dirty rule(s), {} affected element(s) repropagated",
          selective.mode(),
          selective.dirtyRuleCount(),
          selective.affectedElementCount());
      SelectiveOutcome.RuleDiff rules = selective.rules();
      log.info(
          "  {} rule(s) persisted, {} in the new specifications, {} dirty rule(s) with a trigger"
              + " matching a source element",
          rules.persisted(),
          rules.current(),
          rules.matching());
      rules
          .dirty()
          .forEach(
              rule ->
                  log.info(
                      "  rule {} {} matches {} source element(s)",
                      rule.kind().marker(),
                      rule.id(),
                      rule.matchedElements()));
      log.info(
          "  {} source element(s) probed, {} element(s) in the models, {} left out",
          selective.sourceElements(),
          selective.modelElements(),
          selective.leftOutElements().size());
      selective
          .affectedElements()
          .forEach(
              element ->
                  log.info(
                      "  affected {} {} selected by {}",
                      element.key(),
                      element.metaclass(),
                      element.selectedBy()));
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
