package tools.vitruv.dsls.reactions.migration.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
import tools.vitruv.applications.util.temporary.java.JavaSetup;
import tools.vitruv.change.interaction.InteractionResultProvider;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.dsls.reactions.migration.PropagationJars;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;
import tools.vitruv.dsls.reactions.migration.migration.MigrationStatistics.PhaseDuration;
import tools.vitruv.dsls.reactions.migration.preservation.PreservationPolicy;
import tools.vitruv.dsls.reactions.migration.spec.JarSpecificationSource;
import tools.vitruv.dsls.reactions.migration.spec.SpecificationSource;
import tools.vitruv.dsls.reactions.migration.strategy.DominanceStrategy;
import tools.vitruv.dsls.reactions.migration.strategy.ExplicitDominance;
import tools.vitruv.dsls.reactions.migration.strategy.FewestChangesDominance;
import tools.vitruv.dsls.reactions.migration.strategy.MaxReachabilityDominance;
import tools.vitruv.dsls.reactions.migration.strategy.StrategyChoice;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;

@Slf4j
public final class SourceSelectionArtifactGenerator {
  static final String FOLDER = "selection";
  static final String BRAKE_SYSTEM_FILE = "brake-system.properties";

  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();
  private static final MigrationMode MODE = MigrationMode.FULL;
  private static final PreservationPolicy POLICY = PreservationPolicy.REPORT;
  private static final int CONFIGURATIONS = PropagationJars.CONFIGURATIONS;
  private static final String DOMINANCE_PHASE = "dominance";
  private static final String CHANGE_COUNT_PHASE = "change-count";
  private static final String FAILED = "failed";
  private static final Set<String> LIBRARY_TOKENS = Set.of("uml", "java");
  private static final Set<String> BRAKE_TOKENS =
      Set.of("brakesystem", "cad", "simulink", "autosar");
  private static final Comparator<Strategy> STRATEGY_ORDER =
      Comparator.comparingInt(Strategy::order).thenComparing(Strategy::folder);

  @SuppressWarnings("unused")
  private SourceSelectionArtifactGenerator() {}

  public static void main(String[] args) throws Exception {
    Path target = ConfigMatrixArtifactGenerator.resolveTarget(args);
    String step = args.length > 1 ? args[1] : "";
    Path snapshots =
        Path.of(
            System.getProperty(
                ConfigMatrixArtifactGenerator.SNAPSHOTS_PROPERTY,
                ConfigMatrixArtifactGenerator.DEFAULT_SNAPSHOTS));

    Vsums.prepareStandalone(ADAPTERS);
    JavaSetup.resetClasspathAndRegisterStandardLibrary();

    Path selection = target.resolve(FOLDER);
    switch (step) {
      case "cell" ->
          writeCell(
              configuration(args, 2),
              configuration(args, 3),
              strategy(args, 4),
              snapshots,
              selection);
      case "brake" -> writeBrakeSystem(strategy(args, 2), selection);
      case "reset" -> ConfigMatrixFiles.deleteRecursively(selection);
      case "index" -> {
        Files.createDirectories(selection);
        Files.writeString(selection.resolve("README.md"), index(selection));
      }
      default ->
          throw new IllegalArgumentException(
              "No such step: '"
                  + step
                  + "'. One of [cell <from> <to> <strategy>, brake <strategy>, reset, index].");
    }

    log.info("Wrote {} to {}", step, selection.toAbsolutePath());
  }

  private static int configuration(String[] args, int index) {
    if (args.length <= index) {
      throw new IllegalArgumentException(
          "The " + args[1] + " step needs a configuration number as argument " + (index + 1) + ".");
    }

    int number = Integer.parseInt(args[index].trim());
    if (number < 1 || number > CONFIGURATIONS) {
      throw new IllegalArgumentException(
          "No such configuration: " + number + ". There are 1 to " + CONFIGURATIONS + ".");
    }

    return number;
  }

  private static Strategy strategy(String[] args, int index) {
    if (args.length <= index) {
      throw new IllegalArgumentException(
          "The " + args[1] + " step needs a strategy as argument " + (index + 1) + ".");
    }

    return Strategy.of(args[index].trim());
  }

  private static void writeCell(int from, int to, Strategy strategy, Path snapshots, Path selection)
      throws Exception {
    requireToken(strategy, LIBRARY_TOKENS, "library example");
    Path file = selection.resolve(strategy.folder).resolve(cellFileName(from, to));
    Path work = Files.createTempDirectory("selection-" + from + "-to-" + to);
    try {
      Path vsum = ConfigBaselines.materialize(ConfigBaselines.snapshotOf(snapshots, from), work);
      try (JarSpecificationSource specifications =
          JarSpecificationSource.fromJar(PropagationJars.config(to))) {
        Properties run =
            measure(
                specifications,
                vsum,
                strategy,
                "uml",
                new TestUserInteraction.ResultProvider(LibraryCliFixture.permissiveInteraction()));
        run.setProperty("scenario", "library");
        run.setProperty("from", String.valueOf(from));
        run.setProperty("to", String.valueOf(to));
        store(
            file,
            run,
            "The library example derived with config"
                + from
                + ", migrated to the rules of config"
                + to
                + " with the "
                + strategy.folder
                + " strategy.",
            equivalentCommand("umljava-config" + to + ".jar", strategy, "uml"));
      }
    } finally {
      ConfigMatrixFiles.deleteRecursively(work);
    }
  }

  private static void writeBrakeSystem(Strategy strategy, Path selection) throws Exception {
    requireToken(strategy, BRAKE_TOKENS, "brake system");
    Path file = selection.resolve(strategy.folder).resolve(BRAKE_SYSTEM_FILE);
    Path work = Files.createTempDirectory("selection-brake-system");
    try {
      SpecificationSource specifications = BrakeSystemFixture.brakeChain();
      Path vsum = BrakeSystemFixture.seededVsum(work.resolve("brake-vsum"), specifications);
      Properties run =
          measure(specifications, vsum, strategy, "brakesystem", BrakeSystemFixture.answering());
      run.setProperty("scenario", "brake-system");
      store(
          file,
          run,
          "The brake-system chain (brakesystem - cad - simulink - autosar), seeded with a front"
              + " axle and migrated with its own rules by the "
              + strategy.folder
              + " strategy.",
          equivalentCommand("<the brake-system specifications>", strategy, "brakesystem"));
    } finally {
      ConfigMatrixFiles.deleteRecursively(work);
    }
  }

  private static Properties measure(
      SpecificationSource specifications,
      Path vsum,
      Strategy strategy,
      String explicitToken,
      InteractionResultProvider interaction) {
    Properties run = new Properties();
    run.setProperty("strategy", strategy.folder);
    if (strategy.explicit()) {
      run.setProperty("dominantToken", strategy.explicitToken(explicitToken));
    }
    run.setProperty("mode", MODE.name());
    run.setProperty("preservation", POLICY.name());
    run.setProperty("sourceUpdate", "false");

    long graphStartedAt = System.nanoTime();
    PropagationGraph graph = new PropagationGraph(specifications.createSpecifications());
    Duration graphTime = Duration.ofNanos(System.nanoTime() - graphStartedAt);
    run.setProperty("graphMillis", millis(graphTime));

    MigrationRunner runner =
        new MigrationRunner(
            specifications,
            graph,
            strategy.dominance(explicitToken),
            interaction,
            ADAPTERS,
            MigrationSettings.forwardOnly().withMode(MODE).withPreservation(POLICY));
    MigrationReport report;
    try {
      report = runner.run(vsum);
    } catch (MigrationFailure failure) {
      String cause = String.valueOf(ConfigMatrixArtifactGenerator.rootCauseOf(failure));
      run.setProperty("outcome", FAILED);
      run.setProperty("migrated", "false");
      run.setProperty("failure", cause);
      describeChoice(run, failure.sources(), failure.derived(), failure.selection(), graph);
      if (failure.selection().changeCountMeasured()) {
        run.setProperty("changes", String.valueOf(failure.selection().changeCount()));
      }
      log.error("The {} run did not finish: {}", strategy.folder, cause, failure);
      return run;
    } catch (RuntimeException failure) {
      String cause = String.valueOf(ConfigMatrixArtifactGenerator.rootCauseOf(failure));
      run.setProperty("outcome", FAILED);
      run.setProperty("migrated", "false");
      run.setProperty("failure", cause);
      log.error("The {} run did not finish: {}", strategy.folder, cause, failure);
      return run;
    }

    describe(run, report, strategy, graph, graphTime);
    return run;
  }

  private static void describe(
      Properties run,
      MigrationReport report,
      Strategy strategy,
      PropagationGraph graph,
      Duration graphTime) {
    SourceSelection selection = report.selection();
    run.setProperty("outcome", report.migrated() ? "migrated" : "nothing-to-do");
    run.setProperty("migrated", String.valueOf(report.migrated()));
    describeChoice(run, report.sources(), report.derived(), selection, graph);
    run.setProperty("changes", String.valueOf(selection.changeCount()));
    if (report.preservation().failed()) {
      run.setProperty("preservationFailed", report.preservation().failure());
    }

    MigrationStatistics statistics = report.statistics();
    Duration dominance = phaseTotal(statistics, DOMINANCE_PHASE);
    run.setProperty("dominanceMillis", millis(dominance));
    run.setProperty("selectionMillis", millis(selectionTime(strategy, graphTime, dominance)));
    run.setProperty("phases", String.join(",", statistics.phaseNames()));
    Map<String, Duration> perPhase = new LinkedHashMap<>();
    for (PhaseDuration phase : statistics.phases()) {
      perPhase.merge(phase.phase(), phase.duration(), Duration::plus);
    }
    perPhase.forEach((name, duration) -> run.setProperty("phase." + name, millis(duration)));
    run.setProperty("totalMillis", millis(statistics.total()));
    run.setProperty(
        "migrationMillis",
        millis(statistics.total().minus(phaseTotal(statistics, CHANGE_COUNT_PHASE))));
    run.setProperty("interactions", String.valueOf(report.requestedInteractions().size()));
  }

  private static void describeChoice(
      Properties run,
      List<MetamodelNode> sources,
      List<MetamodelNode> derived,
      SourceSelection selection,
      PropagationGraph graph) {
    run.setProperty("present", names(selection.present()));
    if (!sources.isEmpty()) {
      run.setProperty("dominant", sources.getFirst().shortName());
    }
    run.setProperty("sources", names(sources));
    run.setProperty("derived", names(derived));
    describeReach(run, graph, selection.present());
    describeTrials(run, sources, selection);
  }

  private static void describeReach(
      Properties run, PropagationGraph graph, List<MetamodelNode> present) {
    Set<MetamodelNode> presentSet = new LinkedHashSet<>(present);
    Map<MetamodelNode, Rank> ranks = new LinkedHashMap<>();
    for (MetamodelNode node : present) {
      Set<MetamodelNode> reachable = graph.reachableFrom(node);
      long reach = present.stream().filter(o -> !o.equals(node) && reachable.contains(o)).count();
      ranks.put(node, new Rank(graph.outDegree(node), reach));
    }

    run.setProperty(
        "reachabilityRank",
        String.join(
            ",",
            ranks.entrySet().stream()
                .map(
                    e ->
                        e.getKey().shortName()
                            + ":"
                            + e.getValue().outDegree()
                            + "/"
                            + e.getValue().reach())
                .toList()));

    List<MetamodelNode> reachingAll =
        present.stream().filter(node -> graph.reachesAll(node, presentSet)).toList();
    List<MetamodelNode> pool = reachingAll.isEmpty() ? present : reachingAll;
    Comparator<MetamodelNode> order =
        reachingAll.isEmpty()
            ? Comparator.comparingLong((MetamodelNode n) -> ranks.get(n).reach())
                .thenComparingInt(n -> ranks.get(n).outDegree())
            : Comparator.comparingInt((MetamodelNode n) -> ranks.get(n).outDegree())
                .thenComparingLong(n -> ranks.get(n).reach());
    Optional<MetamodelNode> best = pool.stream().max(order);
    long equallyGood =
        best.map(b -> pool.stream().filter(n -> order.compare(n, b) == 0).count()).orElse(0L);
    run.setProperty("reachabilityTie", String.valueOf(equallyGood > 1));
  }

  private static void describeTrials(
      Properties run, List<MetamodelNode> sources, SourceSelection selection) {
    List<SourceSelection.Trial> trials = selection.trials();
    run.setProperty("trials", String.valueOf(trials.size()));
    for (int i = 0; i < trials.size(); i++) {
      SourceSelection.Trial trial = trials.get(i);
      String key = "trial." + i + ".";
      run.setProperty(key + "candidate", trial.candidate().shortName());
      run.setProperty(key + "changes", String.valueOf(trial.changeCount()));
      run.setProperty(key + "millis", millis(trial.duration()));
      trial.failure().ifPresent(reason -> run.setProperty(key + "failure", reason));
    }

    if (sources.isEmpty()) {
      return;
    }

    selection
        .trialOf(sources.getFirst())
        .ifPresent(
            trial -> {
              run.setProperty("predictedChanges", String.valueOf(trial.changeCount()));
              if (trial.completed() && selection.changeCountMeasured()) {
                run.setProperty(
                    "predictionMatches",
                    String.valueOf(trial.changeCount() == selection.changeCount()));
              }
            });
  }

  private static Duration selectionTime(Strategy strategy, Duration graphTime, Duration dominance) {
    return strategy.explicit() ? dominance : graphTime.plus(dominance);
  }

  private static Duration phaseTotal(MigrationStatistics statistics, String name) {
    return statistics.phases().stream()
        .filter(phase -> phase.phase().equals(name))
        .map(PhaseDuration::duration)
        .reduce(Duration.ZERO, Duration::plus);
  }

  private static String names(List<MetamodelNode> nodes) {
    return String.join(",", nodes.stream().map(MetamodelNode::shortName).toList());
  }

  private static String millis(Duration duration) {
    return String.format(Locale.ROOT, "%.3f", duration.toNanos() / 1_000_000.0);
  }

  private static String cellFileName(int from, int to) {
    return "config" + from + "-to-config" + to + ".properties";
  }

  private static String equivalentCommand(
      String propagations, Strategy strategy, String explicitToken) {
    String strategyFlags =
        strategy.explicit()
            ? "--strategy explicit --dominant " + strategy.explicitToken(explicitToken)
            : "--strategy " + strategy.cliToken();
    return "migration --model <vsum> --propagations "
        + propagations
        + " "
        + strategyFlags
        + " --mode full --source-update none --preserve report --ask never";
  }

  private static void store(Path file, Properties run, String what, String command)
      throws IOException {
    Files.createDirectories(file.getParent());
    try (var out = Files.newOutputStream(file)) {
      run.store(out, what + "\nEquivalent command:\n" + command);
    }
  }

  private static String index(Path selection) throws IOException {
    List<Run> runs = readRuns(selection);
    List<Run> library = runs.stream().filter(Run::library).toList();
    List<Run> brake = runs.stream().filter(run -> !run.library()).toList();

    StringBuilder index = new StringBuilder();
    index.append(
        """
        # Choosing the source of truth: the three strategies compared

        Each run is the library example derived with one configuration's rules and migrated to
        another's by a full migration, made once per strategy for choosing the source of truth:

        ```
        migration --model <vsum> --propagations umljava-config<to>.jar \\
                  --strategy explicit --dominant uml \\
                  --mode full --source-update none --preserve report --ask never
        ```

        with `--strategy reachability` and `--strategy derivationLoss` (the fewest-changes strategy)
        in place of the explicit choice, and once more with `--dominant java` (`explicit-java`) as
        the reference for what a migration from the other side changes when nothing ran before it.
        A full migration keeps the source of truth as it is and
        re-derives every other model from it with the new rules, so the strategy decides which
        model survives the migration untouched. The explicit strategy takes the metamodel it is
        told; reachability keeps to the metamodels whose propagations reach every other one and
        takes the one with the most outgoing propagations; the fewest-changes strategy re-derives
        the others from every such candidate into a scratch VSUM first and takes the candidate
        whose re-derivation changes them the least.

        ## What a run records

        - the source of truth the strategy chose (`dominant`), the metamodels present in the order
          the strategies iterate them (`present`), and what the reachability ranking looks like
          (`reachabilityRank`: out-degree / reached present metamodels per node, `reachabilityTie`
          when the best rank is not unique);
        - `changes`: how many atomic changes the re-derivation made to the derived models, counted
          between their files before the migration and the files the rules derived, with elements
          matched by structure rather than identifier. The fewest-changes strategy computes exactly
          this per candidate in its trials (`trial.<n>.*`), so `predictedChanges` for the chosen
          candidate should equal `changes` (`predictionMatches`);
        - `dominanceMillis`: how long the choice took, trial migrations included; `graphMillis`:
          building the propagation graph, which the tool does before every migration;
          `selectionMillis` is what choosing the source costs the strategy - the decision alone
          for the explicit strategy, which needs nothing computed, graph and decision for the
          other two, which read the graph first. `migrationMillis` is the whole migration
          without the time spent counting the changes afterwards (`phase.change-count`), and
          `phase.<name>` holds every phase; a fewest-changes migration adopts the winning trial's
          VSUM (`reuse-trial`) instead of re-deriving the same models again (`re-derive`).

        """);

    long pairs = library.stream().map(run -> run.from() + "-" + run.to()).distinct().count();
    index.append("## Over the ").append(pairs).append(" configuration pairs\n\n");
    index.append(summaryTable(library));
    index.append(
        """

        `selection share` is the median over the runs of `selectionMillis / migrationMillis`.

        ## Changes per configuration pair

        Rows are the configuration the example was derived with, columns the one it was migrated
        to; a cell is `changes`, marked with the source of truth when it is not the one the strategy
        chose in most cells, `fail` when the run did not finish, `?` when it finished but its
        changes could not be counted, and `-` when it was not made.

        """);
    for (Strategy strategy : strategiesOf(library)) {
      List<Run> ofStrategy =
          library.stream().filter(run -> run.strategy().equals(strategy)).toList();
      index.append("### ").append(strategy.folder).append('\n').append(usualPick(ofStrategy));
      index.append(matrix(ofStrategy, Run::changesCell)).append('\n');
      index.append(failureNotes(ofStrategy));
    }

    List<Run> fewest =
        library.stream().filter(run -> run.strategy().equals(Strategy.FEWEST_CHANGES)).toList();
    index.append(
        """
        ## What the fewest-changes strategy tried

        Per configuration pair, the changes each candidate's trial re-derivation proposed, in the
        order the trials ran; `-` marks a trial that did not complete.

        """);
    index.append(matrix(fewest, (run, usualDominant) -> run.trialsCell())).append('\n');
    index.append(trialNotes(fewest));

    index.append("\n## The brake system\n\n");
    index.append(
        """
        The brake-system chain, four metamodels with a propagation each way between neighbours,
        seeded with one front axle and migrated with its own rules. Here the strategies can
        disagree: the explicit strategy is told the brake system (`explicit`) and, for reference,
        each of the other three (`explicit-<metamodel>`), reachability ranks by out-degree and the
        two middle metamodels have the higher one, and the fewest-changes strategy tries all four.

        """);
    index.append(brakeTable(brake));
    index.append(failureNotes(brake));

    index.append("\n## Ties and the order of the models\n\n").append(tieNotes(library, brake));

    index.append(
        """

        ## Regenerating

        Written by `SourceSelectionArtifactGenerator` in the `reactions/migration` module, which
        owns this folder. `reactions/migration/generate-selection-matrix` runs it - one JVM per
        run, one run after the other, because the runs are timed and because JaMoPP's classpath
        registry is global and a VSUM lifecycle leaves it changed. It skips runs whose properties
        file is already there, so an interrupted sweep continues rather than starting over. The
        runs start from the baselines `generate-config-matrix` derives, and use the same
        propagation jars.
        """);
    return index + "\nLast written " + LocalDate.now() + ".\n";
  }

  private static List<Run> readRuns(Path selection) throws IOException {
    List<Run> runs = new ArrayList<>();
    List<Path> folders;
    try (Stream<Path> entries = Files.list(selection)) {
      folders = entries.filter(Files::isDirectory).sorted().toList();
    }

    for (Path folder : folders) {
      Strategy strategy = Strategy.of(folder.getFileName().toString());
      try (Stream<Path> files = Files.list(folder)) {
        for (Path file :
            files.filter(f -> f.toString().endsWith(".properties")).sorted().toList()) {
          Properties properties = new Properties();
          try (var in = Files.newInputStream(file)) {
            properties.load(in);
          }
          runs.add(new Run(strategy, properties));
        }
      }
    }

    runs.sort(Comparator.comparing(Run::strategy, STRATEGY_ORDER));
    return runs;
  }

  private static String summaryTable(List<Run> library) {
    StringBuilder table =
        new StringBuilder(
            """
            | strategy | runs | migrated | source of truth | changes | selection ms | migration ms | selection share |
            |---|---|---|---|---|---|---|---|
            """);
    for (Strategy strategy : strategiesOf(library)) {
      List<Run> runs = library.stream().filter(run -> run.strategy().equals(strategy)).toList();
      List<Run> migrated = runs.stream().filter(Run::migrated).toList();
      table
          .append("| ")
          .append(strategy.folder)
          .append(" | ")
          .append(runs.size())
          .append(" | ")
          .append(migrated.size())
          .append(" | ")
          .append(dominantDistribution(migrated))
          .append(" | ")
          .append(spread(migrated, Run::changes, "%.0f"))
          .append(" | ")
          .append(spread(migrated, Run::selectionMillis, "%.3f"))
          .append(" | ")
          .append(spread(migrated, Run::migrationMillis, "%.0f"))
          .append(" | ")
          .append(
              migrated.isEmpty()
                  ? "-"
                  : String.format(
                      Locale.ROOT,
                      "%.2f %%",
                      100 * median(migrated, run -> run.selectionMillis() / run.migrationMillis())))
          .append(" |\n");
    }

    return table.toString();
  }

  private static String dominantDistribution(List<Run> runs) {
    Map<String, Long> counts = new LinkedHashMap<>();
    runs.forEach(run -> counts.merge(displayName(run.dominant()), 1L, Long::sum));
    if (counts.isEmpty()) {
      return "-";
    }

    return String.join(
        ", ",
        counts.entrySet().stream()
            .map(entry -> "`" + entry.getKey() + "` in " + entry.getValue())
            .toList());
  }

  private static String spread(List<Run> runs, ToDoubleFunction<Run> value, String format) {
    List<Double> values =
        runs.stream().map(value::applyAsDouble).filter(v -> v >= 0).sorted().toList();
    if (values.isEmpty()) {
      return "-";
    }

    String pattern = format + " / " + format + " / " + format;
    return String.format(
        Locale.ROOT, pattern, values.getFirst(), medianOf(values), values.getLast());
  }

  private static double median(List<Run> runs, ToDoubleFunction<Run> value) {
    return medianOf(runs.stream().map(value::applyAsDouble).sorted().toList());
  }

  private static double medianOf(List<Double> sorted) {
    int size = sorted.size();
    if (size == 0) {
      return Double.NaN;
    }

    return size % 2 == 1
        ? sorted.get(size / 2)
        : (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2;
  }

  private static String usualPick(List<Run> runs) {
    Map<String, Long> counts = new LinkedHashMap<>();
    runs.stream().filter(Run::migrated).forEach(run -> counts.merge(run.dominant(), 1L, Long::sum));
    if (counts.isEmpty()) {
      return "\nNo run finished.\n\n";
    }

    String usual =
        counts.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
    long others =
        runs.stream().filter(Run::migrated).filter(run -> !usual.equals(run.dominant())).count();
    return "\nSource of truth: `"
        + displayName(usual)
        + "`"
        + (others == 0
            ? " in every cell.\n\n"
            : " in all but " + others + " cell(s); those are marked with the one they took.\n\n");
  }

  private static String matrix(
      List<Run> runs, java.util.function.BiFunction<Run, String, String> cell) {
    Map<String, Run> byCell = new LinkedHashMap<>();
    runs.forEach(run -> byCell.put(run.from() + "-" + run.to(), run));
    String usual =
        runs.stream()
            .filter(Run::migrated)
            .collect(
                java.util.stream.Collectors.groupingBy(
                    Run::dominant, java.util.stream.Collectors.counting()))
            .entrySet()
            .stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("");

    StringBuilder table = new StringBuilder("| from \\ to |");
    for (int to = 1; to <= CONFIGURATIONS; to++) {
      table.append(" config").append(to).append(" |");
    }
    table.append("\n|---|").repeat("---|", CONFIGURATIONS).append('\n');
    for (int from = 1; from <= CONFIGURATIONS; from++) {
      table.append("| **config").append(from).append("** |");
      for (int to = 1; to <= CONFIGURATIONS; to++) {
        Run run = byCell.get(from + "-" + to);
        table.append(' ').append(run == null ? "-" : cell.apply(run, usual)).append(" |");
      }
      table.append('\n');
    }

    return table.toString();
  }

  private static String trialNotes(List<Run> fewest) {
    List<String> mismatches =
        fewest.stream()
            .filter(run -> "false".equals(run.property("predictionMatches")))
            .map(run -> "config" + run.from() + "-to-config" + run.to())
            .toList();
    List<String> incomplete =
        fewest.stream()
            .filter(run -> run.failedTrials() > 0)
            .map(
                run ->
                    "config"
                        + run.from()
                        + "-to-config"
                        + run.to()
                        + " ("
                        + run.failedTrials()
                        + ")")
            .toList();
    List<String> failed =
        fewest.stream()
            .filter(run -> FAILED.equals(run.outcome()))
            .map(run -> "config" + run.from() + "-to-config" + run.to())
            .toList();

    StringBuilder notes = new StringBuilder();
    notes
        .append("- Cells where the chosen candidate's trial count differs from `changes`: ")
        .append(mismatches.isEmpty() ? "none." : String.join(", ", mismatches) + ".")
        .append('\n');
    notes
        .append("- Trials that did not complete: ")
        .append(incomplete.isEmpty() ? "none." : String.join(", ", incomplete) + ".")
        .append('\n');
    if (!failed.isEmpty()) {
      notes.append("- Runs that did not finish: ").append(String.join(", ", failed)).append(".\n");
    }

    return notes.toString();
  }

  private static String failureNotes(List<Run> runs) {
    StringBuilder notes = new StringBuilder();
    List<Run> failed = runs.stream().filter(run -> FAILED.equals(run.outcome())).toList();
    if (!failed.isEmpty()) {
      notes.append("\nRuns that did not finish:\n\n");
      for (Run run : failed) {
        notes
            .append("- ")
            .append(run.label())
            .append(
                run.dominant().isEmpty()
                    ? ", before a source of truth was chosen: "
                    : ", re-deriving from `" + displayName(run.dominant()) + "`: ")
            .append('`')
            .append(run.property("failure").replace('`', '\''))
            .append("`\n");
      }
    }

    List<Run> unpreserved =
        runs.stream().filter(run -> !run.property("preservationFailed").isEmpty()).toList();
    if (!unpreserved.isEmpty()) {
      notes.append(
          "\nRuns whose preservation pass did not complete (the models are as re-derived):\n\n");
      for (Run run : unpreserved) {
        notes
            .append("- ")
            .append(run.label())
            .append(": `")
            .append(run.property("preservationFailed").replace('`', '\''))
            .append("`\n");
      }
    }

    return notes.toString();
  }

  private static String brakeTable(List<Run> brake) {
    if (brake.isEmpty()) {
      return "Not run.\n";
    }

    StringBuilder table =
        new StringBuilder(
            """
            | strategy | source of truth | re-derived | changes | trials | selection ms | migration ms |
            |---|---|---|---|---|---|---|
            """);
    for (Run run : brake) {
      table
          .append("| ")
          .append(run.strategy().folder)
          .append(" | ")
          .append(run.dominant().isEmpty() ? "-" : "`" + displayName(run.dominant()) + "`")
          .append(" | ")
          .append(displayNames(run.property("derived")))
          .append(" | ")
          .append(run.changesCell(""))
          .append(" | ")
          .append(run.trialsCell())
          .append(" | ")
          .append(
              FAILED.equals(run.outcome())
                  ? "-"
                  : String.format(Locale.ROOT, "%.3f", run.selectionMillis()))
          .append(" | ")
          .append(
              FAILED.equals(run.outcome())
                  ? "-"
                  : String.format(Locale.ROOT, "%.0f", run.migrationMillis()))
          .append(" |\n");
    }

    return table.toString();
  }

  private static String tieNotes(List<Run> library, List<Run> brake) {
    StringBuilder notes = new StringBuilder();
    Set<String> orders = new LinkedHashSet<>();
    library.forEach(run -> orders.add(run.property("present")));
    Set<String> ranks = new LinkedHashSet<>();
    library.forEach(run -> ranks.add(run.property("reachabilityRank")));
    long ties =
        library.stream().filter(run -> "true".equals(run.property("reachabilityTie"))).count();
    if (!orders.isEmpty()) {
      notes
          .append("The library VSUM lists its models as ")
          .append(
              String.join(
                  " and ",
                  orders.stream().map(SourceSelectionArtifactGenerator::displayNames).toList()))
          .append(
              ", which is the order every strategy iterates the metamodels in. The reachability")
          .append(" ranking is ")
          .append(
              String.join(
                  " or ",
                  ranks.stream().map(SourceSelectionArtifactGenerator::displayRanks).toList()))
          .append(" (out-degree / reached present metamodels), a tie in ")
          .append(ties)
          .append(" of ")
          .append(library.size())
          .append(" runs: with one propagation each way, both metamodels reach each other and")
          .append(
              " neither has the higher out-degree, so the strategy keeps the first one listed.\n");
    }

    brake.stream()
        .findFirst()
        .ifPresent(
            run ->
                notes
                    .append("The brake-system VSUM lists ")
                    .append(displayNames(run.property("present")))
                    .append("; the reachability ranking is ")
                    .append(displayRanks(run.property("reachabilityRank")))
                    .append(
                        "true".equals(run.property("reachabilityTie"))
                            ? ", so the best rank is shared and the first of its holders wins.\n"
                            : ", a unique best rank.\n"));
    return notes.toString();
  }

  private static String displayName(String nsUri) {
    if (nsUri == null || nsUri.isEmpty()) {
      return "-";
    }

    String last = URI.createURI(nsUri).lastSegment();
    return last == null || last.isEmpty() ? nsUri : last;
  }

  private static String displayNames(String commaSeparated) {
    if (commaSeparated == null || commaSeparated.isEmpty()) {
      return "-";
    }

    return String.join(
        ", ",
        Arrays.stream(commaSeparated.split(",")).map(n -> "`" + displayName(n) + "`").toList());
  }

  private static String displayRanks(String ranks) {
    if (ranks == null || ranks.isEmpty()) {
      return "-";
    }

    return String.join(
        ", ",
        Arrays.stream(ranks.split(","))
            .map(
                entry -> {
                  int colon = entry.lastIndexOf(':');
                  return colon < 0
                      ? entry
                      : "`"
                          + displayName(entry.substring(0, colon))
                          + "` "
                          + entry.substring(colon + 1);
                })
            .toList());
  }

  private static List<Strategy> strategiesOf(List<Run> runs) {
    return runs.stream().map(Run::strategy).distinct().sorted(STRATEGY_ORDER).toList();
  }

  private static void requireToken(Strategy strategy, Set<String> tokens, String scenario) {
    if (strategy.explicit()
        && strategy.fixedToken() != null
        && !tokens.contains(strategy.fixedToken())) {
      throw new IllegalArgumentException(
          "The "
              + scenario
              + " has no metamodel '"
              + strategy.fixedToken()
              + "'; one of "
              + tokens);
    }
  }

  private record Rank(int outDegree, long reach) {}

  record Strategy(String folder, StrategyChoice choice, String fixedToken) {
    static final Strategy EXPLICIT = new Strategy("explicit", StrategyChoice.EXPLICIT, null);
    static final Strategy REACHABILITY =
        new Strategy("reachability", StrategyChoice.REACHABILITY, null);
    static final Strategy FEWEST_CHANGES =
        new Strategy("fewest-changes", StrategyChoice.FEWEST_CHANGES, null);
    private static final String EXPLICIT_PREFIX = "explicit-";

    static Strategy of(String token) {
      String name = token.toLowerCase(Locale.ROOT);
      if (name.startsWith(EXPLICIT_PREFIX) && name.length() > EXPLICIT_PREFIX.length()) {
        return new Strategy(
            name, StrategyChoice.EXPLICIT, name.substring(EXPLICIT_PREFIX.length()));
      }

      for (Strategy strategy : List.of(EXPLICIT, REACHABILITY, FEWEST_CHANGES)) {
        if (strategy.folder.equals(name)) {
          return strategy;
        }
      }

      return StrategyChoice.fromToken(token)
          .map(
              choice ->
                  switch (choice) {
                    case EXPLICIT -> EXPLICIT;
                    case REACHABILITY -> REACHABILITY;
                    case FEWEST_CHANGES -> FEWEST_CHANGES;
                  })
          .orElseThrow(
              () ->
                  new IllegalArgumentException(
                      "No such strategy: '"
                          + token
                          + "'. One of explicit, explicit-<metamodel>, reachability,"
                          + " fewest-changes, or a --strategy token."));
    }

    String explicitToken(String scenarioToken) {
      return fixedToken == null ? scenarioToken : fixedToken;
    }

    boolean explicit() {
      return choice == StrategyChoice.EXPLICIT;
    }

    String cliToken() {
      return switch (choice) {
        case EXPLICIT -> "explicit";
        case REACHABILITY -> "reachability";
        case FEWEST_CHANGES -> "derivationLoss";
      };
    }

    int order() {
      return switch (choice) {
        case EXPLICIT -> fixedToken == null ? 0 : 1;
        case REACHABILITY -> 2;
        case FEWEST_CHANGES -> 3;
      };
    }

    DominanceStrategy dominance(String scenarioToken) {
      return switch (choice) {
        case EXPLICIT -> new ExplicitDominance(explicitToken(scenarioToken));
        case REACHABILITY -> new MaxReachabilityDominance();
        case FEWEST_CHANGES -> new FewestChangesDominance();
      };
    }
  }

  private record Run(Strategy strategy, Properties properties) {
    String property(String key) {
      return properties.getProperty(key, "");
    }

    String label() {
      return strategy.folder()
          + (library() ? ", config" + from() + "-to-config" + to() : ", brake system");
    }

    boolean library() {
      return "library".equals(property("scenario"));
    }

    int from() {
      return Integer.parseInt(properties.getProperty("from", "0"));
    }

    int to() {
      return Integer.parseInt(properties.getProperty("to", "0"));
    }

    String outcome() {
      return property("outcome");
    }

    boolean migrated() {
      return "true".equals(property("migrated"));
    }

    String dominant() {
      return property("dominant");
    }

    double changes() {
      return Double.parseDouble(properties.getProperty("changes", "-1"));
    }

    double selectionMillis() {
      return Double.parseDouble(properties.getProperty("selectionMillis", "-1"));
    }

    double migrationMillis() {
      return Double.parseDouble(properties.getProperty("migrationMillis", "-1"));
    }

    int trials() {
      return Integer.parseInt(properties.getProperty("trials", "0"));
    }

    long failedTrials() {
      long failed = 0;
      for (int i = 0; i < trials(); i++) {
        if (properties.containsKey("trial." + i + ".failure")) {
          failed++;
        }
      }

      return failed;
    }

    String changesCell(String usualDominant) {
      if (FAILED.equals(outcome())) {
        return "fail";
      }
      if (!migrated()) {
        return "=";
      }

      String changes = changes() < 0 ? "?" : String.valueOf((long) changes());
      return usualDominant.isEmpty() || usualDominant.equals(dominant())
          ? changes
          : changes + " (" + displayName(dominant()) + ")";
    }

    String trialsCell() {
      if (FAILED.equals(outcome())) {
        return "fail";
      }
      if (trials() == 0) {
        return "-";
      }

      List<String> parts = new ArrayList<>();
      for (int i = 0; i < trials(); i++) {
        String candidate = displayName(property("trial." + i + ".candidate"));
        String changes =
            properties.containsKey("trial." + i + ".failure")
                ? "-"
                : property("trial." + i + ".changes");
        parts.add(candidate + " " + changes);
      }

      return String.join(" / ", parts);
    }
  }
}
