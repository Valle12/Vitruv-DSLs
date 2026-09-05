package tools.vitruv.dsls.reactions.migration.migration;

import static tools.vitruv.dsls.reactions.migration.migration.ConfigMatrixFiles.copyFile;
import static tools.vitruv.dsls.reactions.migration.migration.ConfigMatrixFiles.copyTree;
import static tools.vitruv.dsls.reactions.migration.migration.ConfigMatrixFiles.deleteRecursively;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import tools.vitruv.applications.util.temporary.java.JavaSetup;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.dsls.reactions.migration.PropagationJars;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;
import tools.vitruv.dsls.reactions.migration.preservation.LostItem;
import tools.vitruv.dsls.reactions.migration.preservation.PreservationPolicy;
import tools.vitruv.dsls.reactions.migration.preservation.PreservationReportWriter;
import tools.vitruv.dsls.reactions.migration.spec.JarSpecificationSource;
import tools.vitruv.dsls.reactions.migration.strategy.ExplicitDominance;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;

@Slf4j
public final class ConfigMatrixArtifactGenerator {
  public static final String TARGET_PROPERTY = "library.artifacts.target";
  public static final String TARGET_ENVIRONMENT = "LIBRARY_ARTIFACTS_DIR";
  public static final String DEFAULT_CONFIGS = "reactions/preprocessor/src/main/resources/configs";
  public static final String DEFAULT_SNAPSHOTS = "reactions/migration/target/matrix-baselines";
  public static final String SNAPSHOTS_PROPERTY = "library.artifacts.baselines";

  static final String CELL_FILE = "cell.properties";

  private static final String TARGET_MARKER = "config1 UML.drawio";
  private static final int CONFIGURATIONS = PropagationJars.CONFIGURATIONS;
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();
  private static final PreservationPolicy CELL_POLICY = PreservationPolicy.REPORT;
  private static final String CONTENTS_NOTE =
      """

      ## What is here

      `model/library.uml` and `src/catalog/*.java` as this run left them, and the rule registry the
      VSUM carries afterwards. Left out: the standard library stubs JaMoPP derives under
      `src/java`, and the VSUM's own bookkeeping under `vsum`, which only records identities and
      the location it was written at.
      """;

  private ConfigMatrixArtifactGenerator() {}

  public static void main(String[] args) throws Exception {
    Path target = resolveTarget(args);
    Path configs = Path.of(args.length > 1 ? args[1] : DEFAULT_CONFIGS);
    String step = args.length > 2 ? args[2] : "";
    Path snapshots = Path.of(System.getProperty(SNAPSHOTS_PROPERTY, DEFAULT_SNAPSHOTS));

    Vsums.prepareStandalone(ADAPTERS);
    JavaSetup.resetClasspathAndRegisterStandardLibrary();

    Path runs = target.resolve("runs");
    switch (step) {
      case "rules" -> copyRuleSets(configs, target.resolve("rules"));
      case "reset" -> {
        deleteRecursively(runs);
        deleteRecursively(snapshots);
      }
      case "baseline" -> writeBaseline(configurationArgument(args, 3), snapshots, runs);
      case "cell" ->
          writeCell(
              configurationArgument(args, 3), configurationArgument(args, 4), snapshots, runs);
      case "index" -> Files.writeString(runs.resolve("README.md"), index(configs, runs));
      default ->
          throw new IllegalArgumentException(
              "No such step: '"
                  + step
                  + "'. One of [rules, reset, baseline <n>, cell <from> <to>, index].");
    }

    log.info("Wrote {} to {}", step, target.toAbsolutePath());
  }

  private static int configurationArgument(String[] args, int index) {
    if (args.length <= index) {
      throw new IllegalArgumentException(
          "The " + args[2] + " step needs a configuration number as argument " + (index + 1) + ".");
    }

    int number = Integer.parseInt(args[index].trim());
    if (number < 1 || number > CONFIGURATIONS) {
      throw new IllegalArgumentException(
          "No such configuration: " + number + ". There are 1 to " + CONFIGURATIONS + ".");
    }

    return number;
  }

  static Path resolveTarget(String[] args) {
    String configured =
        args.length > 0 && !args[0].isBlank()
            ? args[0]
            : System.getProperty(TARGET_PROPERTY, System.getenv(TARGET_ENVIRONMENT));
    if (configured == null || configured.isBlank()) {
      throw new IllegalArgumentException(
          "No output folder given. Pass it as the first argument, as -D"
              + TARGET_PROPERTY
              + "=<folder> or in "
              + TARGET_ENVIRONMENT
              + ".");
    }

    Path target = Path.of(configured);
    if (!Files.isDirectory(target)) {
      throw new IllegalArgumentException("The output folder does not exist: " + target);
    }
    if (!Files.isRegularFile(target.resolve(TARGET_MARKER))) {
      throw new IllegalArgumentException(
          target.toAbsolutePath()
              + " holds no "
              + TARGET_MARKER
              + ", so it is not the library example folder; refusing to write there");
    }

    return target;
  }

  // ---------------------------------------------------------------- baselines
  private static void writeBaseline(int config, Path snapshots, Path runs) throws Exception {
    Path runFolder = runs.resolve("baselines").resolve("config" + config);
    ConfigBaselines.Baseline baseline;
    try {
      baseline = ConfigBaselines.writeSnapshot(config, snapshots);
      writeStructure(snapshots, config, baseline.snapshot());
    } catch (RuntimeException failure) {
      deleteRecursively(ConfigBaselines.snapshotOf(snapshots, config));
      deleteRecursively(runFolder);
      Files.createDirectories(runFolder);
      Files.writeString(runFolder.resolve("RUN.md"), underivableBaselineNote(config, failure));
      log.warn("config{} cannot derive the example: {}", config, rootCauseOf(failure));
      return;
    }

    List<String> compilerErrors = compilerErrorsIn(baseline.snapshot());
    writeRun(
        baseline.snapshot(),
        runFolder,
        baselineNote(config, baseline.handWrittenContent(), compilerErrors));
    if (!compilerErrors.isEmpty()) {
      log.warn(
          "config{} derives Java that does not compile ({} error(s)): {}",
          config,
          compilerErrors.size(),
          compilerErrors.getFirst());
    }
  }

  private static List<String> compilerErrorsIn(Path vsum) {
    try {
      Path classes = Files.createTempDirectory("matrix-classes");
      try {
        return JavaCompilation.errorsIn(vsum, classes);
      } finally {
        deleteRecursively(classes);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void writeStructure(Path snapshots, int config, Path snapshot) throws Exception {
    Path materialized =
        ConfigBaselines.materialize(snapshot, Files.createTempDirectory("structure"));
    try {
      writeStructureFile(
          structureFile(snapshots, config, "uml"), ModelStructure.ofUml(materialized, ADAPTERS));
      writeStructureFile(
          structureFile(snapshots, config, "java"), ModelStructure.ofJava(materialized, ADAPTERS));
    } finally {
      deleteRecursively(materialized.getParent());
    }
  }

  private static Path structureFile(Path snapshots, int config, String kind) {
    return snapshots.resolve("structure-config" + config + "-" + kind + ".txt");
  }

  private static void writeStructureFile(Path file, Map<String, String> structure)
      throws IOException {
    Files.createDirectories(file.getParent());
    StringBuilder lines = new StringBuilder();
    structure.forEach(
        (name, description) -> lines.append(name).append('\t').append(description).append('\n'));
    Files.writeString(file, lines.toString());
  }

  private static Map<String, String> readStructureFile(Path file) throws IOException {
    if (!Files.isRegularFile(file)) {
      throw new IllegalStateException(
          "No baseline structure at "
              + file.toAbsolutePath()
              + ". Derive that configuration's baseline first.");
    }

    Map<String, String> structure = new LinkedHashMap<>();
    for (String line : Files.readAllLines(file)) {
      int separator = line.indexOf('\t');
      if (separator > 0) {
        structure.put(line.substring(0, separator), line.substring(separator + 1));
      }
    }

    return structure;
  }

  // -------------------------------------------------------------------- cells
  private static void writeCell(int from, int to, Path snapshots, Path runs) throws Exception {
    Path runFolder = runs.resolve(cellFolderName(from, to));
    for (int baseline : new int[] {from, to}) {
      if (!Files.isDirectory(ConfigBaselines.snapshotOf(snapshots, baseline))) {
        writeUnrunnableCell(runFolder, from, to, baseline);
        return;
      }
    }

    Path work = Files.createTempDirectory("matrix-cell-" + from + "-to-" + to);
    try {
      Path vsum = ConfigBaselines.materialize(ConfigBaselines.snapshotOf(snapshots, from), work);
      try (JarSpecificationSource specifications =
          JarSpecificationSource.fromJar(PropagationJars.config(to))) {
        MigrationRunner runner =
            new MigrationRunner(
                specifications,
                new PropagationGraph(specifications.createSpecifications()),
                new ExplicitDominance("uml"),
                new TestUserInteraction.ResultProvider(LibraryCliFixture.permissiveInteraction()),
                ADAPTERS,
                MigrationSettings.forwardOnly()
                    .withMode(MigrationMode.ID_DIFF)
                    .withPreservation(CELL_POLICY));

        MigrationReport report;
        try {
          report = runner.run(vsum);
        } catch (RuntimeException failure) {
          writeFailedCell(runFolder, from, to, failure);
          return;
        }

        ModelStructure.Comparison uml;
        ModelStructure.Comparison java;
        try {
          uml =
              ModelStructure.compare(
                  readStructureFile(structureFile(snapshots, to, "uml")),
                  ModelStructure.ofUml(vsum, ADAPTERS));
          java =
              ModelStructure.compare(
                  readStructureFile(structureFile(snapshots, to, "java")),
                  ModelStructure.ofJava(vsum, ADAPTERS));
        } catch (RuntimeException | AssertionError unreadable) {
          writeUnreadableCell(vsum, runFolder, from, to, unreadable);
          return;
        }

        List<String> compilerErrors = compilerErrorsIn(vsum);
        writeRun(vsum, runFolder, cellNote(from, to, report, uml, java, compilerErrors));
        writeCellProperties(runFolder, from, to, report, uml, java, compilerErrors);
      }
    } finally {
      deleteRecursively(work);
    }
  }

  private static String cellFolderName(int from, int to) {
    return "config" + from + "-to-config" + to;
  }

  private static void writeCellProperties(
      Path runFolder,
      int from,
      int to,
      MigrationReport report,
      ModelStructure.Comparison uml,
      ModelStructure.Comparison java,
      List<String> compilerErrors)
      throws IOException {
    Properties cell = new Properties();
    cell.setProperty("from", String.valueOf(from));
    cell.setProperty("to", String.valueOf(to));
    cell.setProperty("outcome", from == to ? "diagonal" : "migrated");
    cell.setProperty("migrated", String.valueOf(report.migrated()));
    cell.setProperty("fellBackToFull", String.valueOf(report.selective().fellBackToFull()));
    cell.setProperty("dirtyRules", String.valueOf(report.selective().dirtyRuleCount()));
    cell.setProperty("affectedElements", String.valueOf(report.selective().affectedElementCount()));
    cell.setProperty("umlDeviations", String.valueOf(uml.content().size()));
    cell.setProperty("javaDeviations", String.valueOf(java.content().size()));
    cell.setProperty(
        "correspondenceDeviations", String.valueOf(correspondenceDeviations(uml, java)));
    cell.setProperty("umlOrderings", String.valueOf(uml.ordering().size()));
    cell.setProperty("javaOrderings", String.valueOf(java.ordering().size()));
    cell.setProperty("kept", String.valueOf(report.preservation().preserved().size()));
    cell.setProperty("lost", String.valueOf(report.preservation().lost().size()));
    cell.setProperty("undecided", String.valueOf(report.preservation().openDecisions().size()));
    cell.setProperty("manualItems", String.valueOf(report.preservation().manualItems()));
    cell.setProperty("compilerErrors", String.valueOf(compilerErrors.size()));

    SelectiveOutcome selective = report.selective();
    SelectiveOutcome.RuleDiff rules = selective.rules();
    cell.setProperty("rulesPersisted", String.valueOf(rules.persisted()));
    cell.setProperty("rulesCurrent", String.valueOf(rules.current()));
    cell.setProperty(
        "dirtyRulesAdded", String.valueOf(rules.count(SelectiveOutcome.DirtyRule.Kind.ADDED)));
    cell.setProperty(
        "dirtyRulesRemoved", String.valueOf(rules.count(SelectiveOutcome.DirtyRule.Kind.REMOVED)));
    cell.setProperty("dirtyRulesMatching", String.valueOf(rules.matching()));
    cell.setProperty("sourceElements", String.valueOf(selective.sourceElements()));
    cell.setProperty("modelElements", String.valueOf(selective.modelElements()));
    cell.setProperty("matchedElements", String.valueOf(selective.matchedElementCount()));
    cell.setProperty("referrerElements", String.valueOf(selective.referrerElementCount()));
    cell.setProperty("leftOutElements", String.valueOf(selective.leftOutElements().size()));
    cell.setProperty("totalMillis", millis(report.statistics().total()));
    report
        .statistics()
        .phases()
        .forEach(phase -> cell.setProperty("phase." + phase.phase(), millis(phase.duration())));
    try (var out = Files.newOutputStream(runFolder.resolve(CELL_FILE))) {
      cell.store(out, "How " + cellFolderName(from, to) + " came out. Read RUN.md for the prose.");
    }
  }

  private static String millis(Duration duration) {
    return String.format(Locale.ROOT, "%.3f", duration.toNanos() / 1_000_000.0);
  }

  private static int correspondenceDeviations(
      ModelStructure.Comparison uml, ModelStructure.Comparison java) {
    Set<String> subjects = new LinkedHashSet<>();
    uml.content().forEach(deviation -> subjects.add(subjectOf(deviation)));
    java.content().forEach(deviation -> subjects.add(subjectOf(deviation)));
    return subjects.size();
  }

  private static String subjectOf(String deviation) {
    String headline = deviation.lines().findFirst().orElse(deviation);
    int afterVerb = headline.indexOf("  ");
    String subject = afterVerb < 0 ? headline : headline.substring(afterVerb + 2);
    int description = subject.indexOf(" -> ");
    return (description < 0 ? subject : subject.substring(0, description)).trim();
  }

  private static void writeFailedCell(Path runFolder, int from, int to, RuntimeException failure)
      throws IOException {
    deleteRecursively(runFolder);
    Files.createDirectories(runFolder);
    Files.writeString(runFolder.resolve("RUN.md"), failedNote(from, to, failure));

    Properties cell = new Properties();
    cell.setProperty("from", String.valueOf(from));
    cell.setProperty("to", String.valueOf(to));
    cell.setProperty("outcome", "failed");
    cell.setProperty("failure", String.valueOf(rootCauseOf(failure)));
    try (var out = Files.newOutputStream(runFolder.resolve(CELL_FILE))) {
      cell.store(out, "This cell did not finish.");
    }

    log.error("config{} -> config{} did not finish: {}", from, to, rootCauseOf(failure), failure);
  }

  private static void writeUnreadableCell(
      Path vsum, Path runFolder, int from, int to, Throwable unreadable) throws IOException {
    writeRun(vsum, runFolder, unreadableNote(from, to, unreadable));

    Properties cell = new Properties();
    cell.setProperty("from", String.valueOf(from));
    cell.setProperty("to", String.valueOf(to));
    cell.setProperty("outcome", "unreadable");
    cell.setProperty("failure", String.valueOf(rootCauseOf(unreadable)));
    try (var out = Files.newOutputStream(runFolder.resolve(CELL_FILE))) {
      cell.store(out, "This cell migrated, but what it produced does not load.");
    }

    log.warn(
        "config{} -> config{} produced models that do not load: {}",
        from,
        to,
        rootCauseOf(unreadable));
  }

  private static void writeUnrunnableCell(Path runFolder, int from, int to, int missing)
      throws IOException {
    deleteRecursively(runFolder);
    Files.createDirectories(runFolder);
    Files.writeString(runFolder.resolve("RUN.md"), unrunnableNote(from, to, missing));

    Properties cell = new Properties();
    cell.setProperty("from", String.valueOf(from));
    cell.setProperty("to", String.valueOf(to));
    cell.setProperty("outcome", "unrunnable");
    cell.setProperty("missingBaseline", String.valueOf(missing));
    try (var out = Files.newOutputStream(runFolder.resolve(CELL_FILE))) {
      cell.store(out, "This cell has no baseline to run against.");
    }

    log.warn(
        "config{} -> config{} needs the config{} baseline, which could not be derived.",
        from,
        to,
        missing);
  }

  static String rootCauseOf(Throwable failure) {
    Throwable cause = failure;
    while (cause.getCause() != null && cause.getCause() != cause) {
      cause = cause.getCause();
    }

    return cause.getMessage();
  }

  // ------------------------------------------------------------------ writing
  private static void writeRun(Path vsumFolder, Path runFolder, String note) throws IOException {
    deleteRecursively(runFolder);
    copyModelFiles(vsumFolder.resolve("model"), runFolder.resolve("model"), ".uml");
    copyModelFiles(vsumFolder.resolve("src"), runFolder.resolve("src"), ".java");
    Path ruleHashes = Path.of("consistencymetadata", "vitruv", "rule-hashes.txt");
    copyFile(vsumFolder.resolve(ruleHashes), runFolder.resolve(ruleHashes));
    Path report = vsumFolder.resolve(PreservationReportWriter.FILE_NAME);
    if (Files.exists(report)) {
      Files.writeString(
          runFolder.resolve(PreservationReportWriter.FILE_NAME),
          withoutTheScratchPath(Files.readString(report)));
    }

    Files.writeString(runFolder.resolve("RUN.md"), note);
  }

  private static void copyModelFiles(Path source, Path target, String extension)
      throws IOException {
    for (Path file : ModelStructure.modelFiles(source, extension)) {
      copyFile(file, target.resolve(source.relativize(file).toString()));
    }
  }

  private static void copyRuleSets(Path configs, Path rulesFolder) throws IOException {
    if (!Files.isDirectory(configs)) {
      throw new IllegalArgumentException(
          "The preprocessor configs folder does not exist: "
              + configs.toAbsolutePath()
              + ". Pass it as the second argument.");
    }

    deleteRecursively(rulesFolder);
    for (int config = 1; config <= CONFIGURATIONS; config++) {
      Path reactions = configs.resolve("config" + config + "-reactions");
      if (!Files.isDirectory(reactions)) {
        throw new IllegalArgumentException(
            "The preprocessed reactions are missing: "
                + reactions.toAbsolutePath()
                + ". Run reactions/preprocessor/preprocess-configs first.");
      }

      copyFile(
          configs.resolve("config" + config + ".json"),
          rulesFolder.resolve("config" + config + ".json"));
      copyTree(reactions, rulesFolder.resolve("config" + config + "-reactions"));
    }
  }

  private static String withoutTheScratchPath(String report) {
    return report.replaceAll(
        "Recover these from the models in `[^`]*`, not from the ones next to this report - those"
            + " are the migrated ones\\.",
        "These were kept in a folder beside the one that was migrated. This example was generated"
            + " in a scratch area that has since been deleted, so there is nothing to point at"
            + " here; a real migration names that folder at this spot and leaves it in place.");
  }

  // -------------------------------------------------------------------- prose
  private static String baselineNote(
      int config, boolean handWrittenContent, List<String> compilerErrors) {
    StringBuilder compilation = new StringBuilder();
    appendCompilerErrors(compilation, compilerErrors);
    return "# The config"
        + config
        + " baseline\n"
        + """

        The library example as this configuration's rules derive it into an empty VSUM, with no
        migration involved. Every cell in the row `config"""
        + config
        + """
        -to-*` starts from this state, and every
        cell in the column `*-to-config"""
        + config
        + """
        ` is read against it: a migration that ends where this
        does has re-derived everything the target rules would have produced anyway.

        Rules: `umljava-config"""
        + config
        + """
        .jar`, built from `../rules/config"""
        + config
        + """
        -reactions`.

        """
        + handWrittenContentNote(handWrittenContent)
        + compilation
        + CONTENTS_NOTE;
  }

  private static String handWrittenContentNote(boolean handWrittenContent) {
    if (handWrittenContent) {
      return """
          It carries one thing the rules do not derive: the body of `Member.totalWeight`, which sums
          the weights of the media a member borrowed. The rules derive that method's signature from
          the UML operation and nothing about what it computes. That body is what the preservation
          pass has to account for, and what each cell's `preservation-report.md` is about.
          """;
    }

    return """
        The other baselines carry one thing the rules do not derive - the body of
        `Member.totalWeight` - and this one cannot. This configuration realizes the classifiers
        holding it as Java interfaces, an interface method carries no body, and the Java metamodel
        here predates `default`, so there is nowhere to write the statements. A row starting here
        therefore has no hand-written content for the preservation pass to report on. That is a
        property of the configuration, not of the migration.
        """;
  }

  private static String cellNote(
      int from,
      int to,
      MigrationReport report,
      ModelStructure.Comparison uml,
      ModelStructure.Comparison java,
      List<String> compilerErrors) {
    StringBuilder note = new StringBuilder();
    note.append("# config").append(from).append(" migrated to config").append(to).append("\n\n");
    if (from == to) {
      note.append(
          """
          Source and target rules are the same, so this cell is the one that must not change
          anything: the persisted rule registry already matches the new specifications, no rule is
          dirty, and the selective migration returns without opening a view.

          """);
    }

    note.append(
            """
            | | |
            |---|---|
            """)
        .append(row("input", "the config" + from + " baseline, `baselines/config" + from + "`"))
        .append(row("rules", "`umljava-config" + to + ".jar`"))
        .append(row("dominance", "explicit, uml"))
        .append("\nEquivalent command:\n\n```\n")
        .append("migration --model <vsum> --propagations umljava-config")
        .append(to)
        .append(".jar \\\n")
        .append("          --strategy explicit --dominant uml \\\n")
        .append("          --mode ids --source-update none --preserve ")
        .append(CELL_POLICY.name().toLowerCase(Locale.ROOT))
        .append(" --ask never\n```\n");

    note.append("\n## What the migration reported\n\n")
        .append("| | |\n|---|---|\n")
        .append(row("migrated", report.migrated()))
        .append(row("migration mode", report.selective().mode()))
        .append(row("fell back to a full migration", report.selective().fellBackToFull()))
        .append(row("reason for the fallback", report.selective().fallbackReason().orElse("-")))
        .append(row("dirty rules", report.selective().dirtyRuleCount()))
        .append(row("affected elements", report.selective().affectedElementCount()))
        .append(row("preservation policy", CELL_POLICY))
        .append(row("elements that would be kept", report.preservation().preserved().size()))
        .append(row("elements that could not be kept", report.preservation().lost().size()))
        .append(row("open decisions nobody answered", report.preservation().openDecisions().size()))
        .append(row("items left to deal with by hand", report.preservation().manualItems()))
        .append(row("phases", String.join(", ", report.statistics().phaseNames())));

    appendSelection(note, report.selective());

    note.append("\n## Against config").append(to).append(" from scratch\n\n");
    if (uml.agrees() && java.agrees()) {
      note.append("The migrated models describe exactly what `baselines/config").append(to);
      note.append(
          "` describes. Under `--preserve report` nothing is carried over into the models, so"
              + " what is left is what the target rules derive - and that is what the baseline"
              + " derives from the same UML.\n");
    } else if (uml.content().isEmpty() && java.content().isEmpty()) {
      note.append("The migrated models hold exactly what `baselines/config").append(to);
      note.append(
          "` holds, listed in a different order. Nothing is missing, added or derived"
              + " differently; a classifier's members simply came back in the order the"
              + " repropagation rebuilt them in rather than the order a derivation from scratch"
              + " produces.\n");
    } else {
      note.append("The migrated models and `baselines/config").append(to);
      note.append("` do not describe the same thing about ")
          .append(correspondenceDeviations(uml, java));
      note.append(
          " classifier(s), which is the number the matrix carries. Each line below names a"
              + " classifier and how the two differ, per model, so a classifier wrong in both is"
              + " listed twice here and counted once there; `expected` is the baseline.\n");
    }

    appendDeviations(note, "UML", uml.content());
    appendDeviations(note, "Java", java.content());
    appendDeviations(note, "UML, same content in another order", uml.ordering());
    appendDeviations(note, "Java, same content in another order", java.ordering());

    List<String> lost =
        report.preservation().lost().stream()
            .map(ConfigMatrixArtifactGenerator::describe)
            .sorted(Comparator.naturalOrder())
            .toList();
    if (!lost.isEmpty()) {
      note.append("\n## What could not be kept\n\n");
      lost.forEach(item -> note.append("- ").append(item).append('\n'));
    }

    appendCompilerErrors(note, compilerErrors);
    return note + CONTENTS_NOTE;
  }

  private static void appendSelection(StringBuilder note, SelectiveOutcome selective) {
    SelectiveOutcome.RuleDiff rules = selective.rules();
    String dirty =
        rules.dirty().size()
            + " ("
            + rules.count(SelectiveOutcome.DirtyRule.Kind.ADDED)
            + " added, "
            + rules.count(SelectiveOutcome.DirtyRule.Kind.REMOVED)
            + " removed"
            + (rules.count(SelectiveOutcome.DirtyRule.Kind.CHANGED) > 0
                ? ", " + rules.count(SelectiveOutcome.DirtyRule.Kind.CHANGED) + " changed"
                : "")
            + ")";
    note.append("\n## What the rule diff and the trigger mechanism found\n\n")
        .append("| | |\n|---|---|\n")
        .append(row("rules in the persisted registry", rules.persisted()))
        .append(row("rules in the new specifications", rules.current()))
        .append(row("dirty rules", dirty))
        .append(row("dirty rules whose trigger matches a source element", rules.matching()))
        .append(row("source elements probed", selective.sourceElements()))
        .append(row("elements in the models", selective.modelElements()))
        .append(row("matched elements", selective.matchedElementCount()))
        .append(row("elements added as referrers", selective.referrerElementCount()))
        .append(row("elements left out", selective.leftOutElements().size()));
    if (rules.dirty().isEmpty()) {
      note.append("\nNothing is dirty, so nothing was selected.\n");
      return;
    }

    note.append("\n### Dirty rules\n\n```\n");
    rules
        .dirty()
        .forEach(
            rule ->
                note.append(rule.kind().marker())
                    .append(' ')
                    .append(rule.id())
                    .append("  matches ")
                    .append(rule.matchedElements())
                    .append(" source element(s)\n"));
    note.append("```\n");

    note.append("\n### Affected elements\n\n");
    if (selective.affectedElements().isEmpty()) {
      note.append("None; the dirty rules match no source element.\n");
    } else {
      note.append("```\n");
      selective
          .affectedElements()
          .forEach(
              element ->
                  note.append(shortKey(element.key()))
                      .append("  ")
                      .append(element.metaclass())
                      .append("  ")
                      .append(
                          element
                              .refersTo()
                              .map(target -> "refers to " + shortKey(target))
                              .orElseGet(
                                  () -> "matched by " + String.join(", ", element.matchedBy())))
                      .append('\n'));
      note.append("```\n");
    }

    note.append("\n### Left out\n\n");
    if (selective.leftOutElements().isEmpty()) {
      note.append("None.\n");
    } else {
      note.append("```\n");
      selective
          .leftOutElements()
          .forEach(
              element ->
                  note.append(shortKey(element.key()))
                      .append("  ")
                      .append(element.metaclass())
                      .append("  ")
                      .append(withoutTheScratchFolder(element.reason()))
                      .append('\n'));
      note.append("```\n");
    }
  }

  private static String shortKey(String key) {
    int fragment = key.indexOf('#');
    if (fragment < 0) {
      return key;
    }

    return key.substring(key.lastIndexOf('/', fragment) + 1);
  }

  private static String withoutTheScratchFolder(String reason) {
    return reason.replaceAll("file:/\\S*?/library-vsum/", "");
  }

  private static void appendCompilerErrors(StringBuilder note, List<String> compilerErrors) {
    note.append("\n## Does the Java compile\n\n");
    if (compilerErrors.isEmpty()) {
      note.append("Yes; `javac` reports nothing about `src/catalog`.\n");
      return;
    }

    note.append("No, ")
        .append(compilerErrors.size())
        .append(compilerErrors.size() == 1 ? " error:\n\n```\n" : " errors:\n\n```\n");
    compilerErrors.forEach(error -> note.append(error).append('\n'));
    note.append("```\n");
  }

  private static void appendDeviations(StringBuilder note, String label, List<String> deviations) {
    if (deviations.isEmpty()) {
      return;
    }

    note.append("\n### ")
        .append(label)
        .append(", ")
        .append(deviations.size())
        .append(deviations.size() == 1 ? " place\n\n```\n" : " places\n\n```\n");
    deviations.forEach(deviation -> note.append(deviation).append('\n'));
    note.append("```\n");
  }

  private static String failedNote(int from, int to, RuntimeException failure) {
    return "# config"
        + from
        + " migrated to config"
        + to
        + """


        This run did not finish, and there are no models here because of it.

        The migration was started with `--mode ids` and stopped with:

        ```
        """
        + rootCauseOf(failure)
        + """

        ```

        The models it left behind are not written here: a run that stopped part way through is
        neither the state it started from nor the one it was migrating to, and putting it beside the
        cells that finished would invite reading it as a result. `baselines/config"""
        + from
        + """
        ` is the state
        this run started from, and `baselines/config"""
        + to
        + """
        ` is where it was trying to get to.
        """;
  }

  private static String underivableBaselineNote(int config, RuntimeException failure) {
    return "# The config"
        + config
        + " baseline"
        + """


        This configuration's rules cannot derive the library example at all, so there is no baseline
        here and no models. Deriving it stopped with:

        ```
        """
        + rootCauseOf(failure)
        + """

        ```

        Nothing has been migrated at this point - this is the rules deriving the example into an
        empty VSUM. The row and the column of this configuration are therefore unrunnable rather
        than failed: there is no state to migrate from, and nothing to read a migration against.
        """;
  }

  private static String unreadableNote(int from, int to, Throwable unreadable) {
    return "# config"
        + from
        + " migrated to config"
        + to
        + """


        The migration finished, and what it left cannot be read back:

        ```
        """
        + rootCauseOf(unreadable)
        + """

        ```

        The models are here, because a result that does not load is still the result and is what
        there is to look at. What is not here is a comparison against the target configuration
        derived from scratch: there is nothing to compare a model that does not load with. Read this
        as the migration having produced something the rules would never derive - `baselines/config"""
        + to
        + """
        `
        is what they do derive from the same UML.
        """
        + CONTENTS_NOTE;
  }

  private static String unrunnableNote(int from, int to, int missing) {
    return "# config"
        + from
        + " migrated to config"
        + to
        + """


        This cell was not run. It needs the config"""
        + missing
        + """
         baseline, and that configuration's rules
        cannot derive the library example - see `baselines/config"""
        + missing
        + """
        ` for what stopped it.

        """
        + (missing == from
            ? "There is no state for this row to start from."
            : "There is nothing to read this column's migrations against.")
        + "\n";
  }

  private static String describe(LostItem item) {
    return item.element()
        + " - "
        + item.reason().getDescription()
        + (item.detail() == null ? "" : " (" + item.detail() + ")");
  }

  private static String row(String label, Object value) {
    return "| " + label + " | " + value + " |\n";
  }

  // -------------------------------------------------------------------- index
  private static String index(Path configs, Path runs) throws IOException {
    StringBuilder index = new StringBuilder();
    index.append(
        """
        # The library example across every pair of configurations

        Each cell is the library example derived with one configuration's rules and then migrated to
        another's, with

        ```
        migration --model <vsum> --propagations umljava-config<to>.jar \\
                  --strategy explicit --dominant uml \\
                  --mode ids --source-update none --preserve report --ask never
        ```

        and read against `baselines/config<to>`, the same example derived with the target rules into
        an empty VSUM. `--mode ids` is the selective migration: it works out which rules changed by
        their ids and repropagates only the elements those rules affect, tearing them down before
        the new rules build them up again. What that came to in a cell - the dirty rules, the
        source elements their triggers selected and the ones left out - is in the cell's `RUN.md`
        under *What the rule diff and the trigger mechanism found*.

        ## The matrix

        Rows are the configuration the example was derived with, columns the one it was migrated to.

        """);

    Set<String> legendKeysUsed = new LinkedHashSet<>();
    CompileTally compile = new CompileTally();
    String table = matrixTable(runs, legendKeysUsed, compile);
    index.append(table).append('\n');
    index.append(legend(legendKeysUsed));
    index.append(
        """

        ## What a migration does not undo

        Leaving a feature behind does not take back what it put into the dominant model. The rules
        of the target configuration know nothing about the elements an earlier one added, and the
        tear-down works from rule ids, so a UML construct the source rules introduced simply stays.
        `config5-to-config8` keeps `Member()` and its siblings, the default constructors
        `ConstructorCreation` derives, in classifiers config8 realizes as Java enums - where a
        public constructor is not legal Java. `config4-to-config1` keeps the `Impl` suffix
        `RealizationSuffix` appended. Both are counted as deviations above; neither is a defect of
        the migration, and undoing them would mean tearing down dominant-model elements on the
        strength of rules that are no longer there.

        ## Why a cell is comparable to a from-scratch derivation at all

        Because these runs use `--preserve report`. The preservation pass then works out what the
        old models hold that no rule produced and writes it down without putting any of it back, so
        what is left in a cell is what the target rules derive. Running the same migration with
        `--preserve user` puts that content back, and a cell would then legitimately hold more than
        the baseline - the difference would be the carried-over content and not a fidelity result.
        Each cell's `preservation-report.md` is that list: the items to go through by hand after a
        real migration; its `Left to deal with by hand` line is the `manual` number in the table.
        """);
    index.append(compileSection(compile));
    index.append(
        """

        ## The configurations

        """);
    index.append(configurationTable(configs)).append('\n');
    index.append(
        """
        The feature model is `reactions/preprocessor/src/main/resources/feature-model.uvl`, the
        selections are `../rules/config<n>.json`, and `../rules/config<n>-reactions` is what the
        preprocessor makes of the annotated umljava reactions under each of them.

        ## Regenerating

        Written by `ConfigMatrixArtifactGenerator` in the `reactions/migration` module, which owns
        `../rules` and this folder and rewrites both. Everything else beside them is left alone.
        `reactions/migration/generate-config-matrix` runs it - one JVM per cell, because JaMoPP's
        classpath registry is global and a VSUM lifecycle leaves it changed, so cells sharing a
        process do not derive the same models. It skips cells that already have a `RUN.md`, so an
        interrupted sweep continues rather than starting over.

        Regenerating with nothing else changed rewrites the `xmi:id` values in the derived
        `library.uml` files and nothing more, so only regenerate when the models or the rules have
        actually moved. The rule sets under `../rules` are the preprocessor's output and have to be
        regenerated in the same session as the jars the runs use, or they would describe rules the
        runs did not use; `reactions/migration/build-propagation-jars` builds those jars and
        `reactions/migration/src/test/resources/propagations/README.md` describes the procedure.
        """);

    return index + "\nLast written " + LocalDate.now() + ".\n";
  }

  private static String legend(Set<String> used) {
    Map<String, String> entries = new LinkedHashMap<>();
    entries.put(
        "=",
        """
        - `=` - source and target rules are the same. Nothing is dirty, the migration returns
          without opening a view, and the models are the ones the row's baseline holds. This is the
          cell that would show a selective migration doing work it has no reason to do.
        """);
    entries.put(
        "ok",
        """
        - `ok` - the migrated models describe exactly what the target configuration derives from
          scratch.
        """);
    entries.put(
        "ok (order)",
        """
        - `ok (order)` - they hold exactly the same content, listed in a different order. Nothing is
          missing, added or derived differently: a classifier's members came back in the order the
          repropagation rebuilt them in rather than the order a derivation from scratch produces.
          The cell's `RUN.md` lists these apart from real differences.
        """);
    entries.put(
        "dev",
        """
        - `N dev` - the migrated models and the baseline disagree about N classifiers, ordering
          aside. A classifier is counted once however many of the two models it is wrong in, since
          the UML and the Java derived beside it are one thing to go and look at. The cell's
          `RUN.md` names each one, per model.
        """);
    entries.put(
        "manual",
        """
        - `/ M manual` - M items of the old models are left to be dealt with by hand: content no
          rule of the target configuration derives (the report's `Would be kept`), content the
          pass could not place at all (`Not kept`), and decisions nobody made (`Open decisions`).
          These runs use `--preserve report`, so none of it is put back; M is the sum of those
          three section headers, stated in the report's own `Left to deal with by hand` line. The
          report's `Notes` section - moves, replaced values, carriers - is informational and not
          counted.
        """);
    entries.put(
        "fail",
        """
        - `fail` - the migration did not finish. The cell holds a `RUN.md` saying why and no models.
        """);
    entries.put(
        "unreadable",
        """
        - `unreadable` - the migration finished and what it produced does not load, so there is
          nothing to compare. The models are in the cell anyway; its `RUN.md` says what stopped
          reading them.
        """);
    entries.put(
        "n/a",
        """
        - `n/a` - the cell was not run, because a configuration it needs cannot derive the example
          into an empty VSUM in the first place. That is a property of that rule set and shows up
          before any migration is involved; `baselines/config<n>` says what stopped it.
        """);
    entries.put("-", "- `-` - not generated.\n");

    StringBuilder legend = new StringBuilder();
    entries.forEach(
        (key, text) -> {
          if (used.contains(key)) {
            legend.append(text);
          }
        });
    return legend.toString();
  }

  private static String compileSection(CompileTally compile) {
    StringBuilder section =
        new StringBuilder(
            """

            ## Does any of it compile

            Every cell and every baseline is run through `javac`, and its `RUN.md` says what came
            back. That is a separate result from the comparison above: a cell can hold exactly what
            the target rules derive and still not compile, because a model comparison does not know
            what Java requires.
            """);
    if (compile.migrated > 0) {
      section
          .append("`javac` rejects the Java of ")
          .append(compile.failing)
          .append(" of the ")
          .append(compile.migrated)
          .append(" migrated cells.\n");
    }

    section.append(
        """
        The empty `Member.totalWeight` is the usual reason - the rules derive its signature and
        never its body, `--preserve report` puts the body back nowhere, and a `double` method
        without a `return` is not Java. That is this policy showing rather than a defect;
        `--preserve user` is the run that puts the statements back.
        """);

    List<Integer> clean = compile.cleanColumns();
    if (compile.failing > 0 && !clean.isEmpty()) {
      section
          .append("\nThe exception is the ")
          .append(columnNames(clean))
          .append(
              clean.size() == 1
                  ? " column: every migration into it compiles despite"
                  : " columns: every migration into them compiles despite")
          .append(" losing the body,\n")
          .append("because those rules realize the classifiers holding it as Java interfaces,")
          .append(" and a\nbody-less interface method is legal Java. The loss is the same, just")
          .append(" silent to `javac` -\nthe cell's `preservation-report.md` is what still")
          .append(" records it.\n");
    }

    return section.toString();
  }

  private static String columnNames(List<Integer> columns) {
    List<String> names = columns.stream().map(column -> "`config" + column + "`").toList();
    return names.size() == 1
        ? names.getFirst()
        : String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
  }

  private static String matrixTable(Path runs, Set<String> legendKeysUsed, CompileTally compile)
      throws IOException {
    StringBuilder table = new StringBuilder("| from \\ to |");
    for (int to = 1; to <= CONFIGURATIONS; to++) {
      table.append(" config").append(to).append(" |");
    }
    table.append("\n|---|").repeat("---|", CONFIGURATIONS).append('\n');

    for (int from = 1; from <= CONFIGURATIONS; from++) {
      table.append("| **config").append(from).append("** |");
      for (int to = 1; to <= CONFIGURATIONS; to++) {
        Properties cell = cellProperties(runs, from, to);
        if (cell != null) {
          compile.count(from, to, cell);
        }

        CellSummary summary = cellSummary(cell, from, to);
        legendKeysUsed.addAll(summary.legendKeys());
        table.append(' ').append(summary.text()).append(" |");
      }
      table.append('\n');
    }

    return table.toString();
  }

  private static Properties cellProperties(Path runs, int from, int to) throws IOException {
    Path cellFile = runs.resolve(cellFolderName(from, to)).resolve(CELL_FILE);
    if (!Files.isRegularFile(cellFile)) {
      return null;
    }

    Properties cell = new Properties();
    try (var in = Files.newInputStream(cellFile)) {
      cell.load(in);
    }

    return cell;
  }

  private static CellSummary cellSummary(Properties cell, int from, int to) {
    if (cell == null) {
      return CellSummary.of("-", "-");
    }

    if ("failed".equals(cell.getProperty("outcome"))) {
      return CellSummary.of("fail", "fail");
    }
    if ("unrunnable".equals(cell.getProperty("outcome"))) {
      return CellSummary.of("n/a", "n/a");
    }
    if ("unreadable".equals(cell.getProperty("outcome"))) {
      return CellSummary.of("unreadable", "unreadable");
    }

    int deviations = Integer.parseInt(cell.getProperty("correspondenceDeviations", "0"));
    int orderings =
        Integer.parseInt(cell.getProperty("umlOrderings", "0"))
            + Integer.parseInt(cell.getProperty("javaOrderings", "0"));
    int manual = Integer.parseInt(cell.getProperty("manualItems", "0"));

    String base;
    String baseKey;
    if (from == to) {
      base = deviations == 0 && orderings == 0 ? "=" : "= " + deviations + " dev";
      baseKey = deviations == 0 && orderings == 0 ? "=" : "dev";
    } else if (deviations > 0) {
      base = deviations + " dev";
      baseKey = "dev";
    } else {
      base = orderings == 0 ? "ok" : "ok (order)";
      baseKey = orderings == 0 ? "ok" : "ok (order)";
    }

    return manual == 0
        ? CellSummary.of(base, baseKey)
        : CellSummary.of(base + " / " + manual + " manual", baseKey, "manual");
  }

  private static String configurationTable(Path configs) throws IOException {
    List<String> baseline = featuresOf(configs, 1);
    StringBuilder table =
        new StringBuilder("| configuration | how it differs from config1 |\n|---|---|\n");
    table.append("| config1 | the baseline: `ClassCreation.Class`, `DataTypeCreation.Class` |\n");
    for (int config = 2; config <= CONFIGURATIONS; config++) {
      List<String> features = featuresOf(configs, config);
      List<String> added = new ArrayList<>(features);
      added.removeAll(baseline);
      List<String> removed = new ArrayList<>(baseline);
      removed.removeAll(features);

      List<String> delta = new ArrayList<>();
      added.forEach(feature -> delta.add("`+ " + feature + "`"));
      removed.forEach(feature -> delta.add("`- " + feature + "`"));
      table
          .append("| config")
          .append(config)
          .append(" | ")
          .append(delta.isEmpty() ? "nothing" : String.join(", ", delta))
          .append(" |\n");
    }

    return table.toString();
  }

  private static List<String> featuresOf(Path configs, int config) throws IOException {
    String json = Files.readString(configs.resolve("config" + config + ".json"));
    return Stream.of(json.replace('[', ' ').replace(']', ' ').split(","))
        .map(entry -> entry.replace("\"", "").trim())
        .filter(entry -> !entry.isEmpty())
        .toList();
  }

  private static final class CompileTally {
    private final Map<Integer, Boolean> columnCompiles = new LinkedHashMap<>();
    private int migrated;
    private int failing;

    void count(int from, int to, Properties cell) {
      String errors = cell.getProperty("compilerErrors");
      if (from == to || errors == null) {
        return;
      }

      boolean compiles = Integer.parseInt(errors) == 0;
      migrated++;
      if (!compiles) {
        failing++;
      }

      columnCompiles.merge(to, compiles, Boolean::logicalAnd);
    }

    List<Integer> cleanColumns() {
      return columnCompiles.entrySet().stream()
          .filter(Map.Entry::getValue)
          .map(Map.Entry::getKey)
          .sorted()
          .toList();
    }
  }

  private record CellSummary(String text, List<String> legendKeys) {
    static CellSummary of(String text, String... legendKeys) {
      return new CellSummary(text, List.of(legendKeys));
    }
  }
}
