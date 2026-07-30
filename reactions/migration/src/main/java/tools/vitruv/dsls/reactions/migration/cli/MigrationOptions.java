package tools.vitruv.dsls.reactions.migration.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import tools.vitruv.dsls.reactions.migration.migration.MigrationMode;
import tools.vitruv.dsls.reactions.migration.preservation.PreservationPolicy;
import tools.vitruv.dsls.reactions.migration.strategy.StrategyChoice;

public record MigrationOptions(
    Path vsumFolder,
    Path specificationsJar,
    StrategyChoice strategy,
    Optional<String> dominantToken,
    SourceUpdateMode sourceUpdate,
    int maxSourceUpdateRounds,
    boolean backup,
    MigrationMode mode,
    PreservationPolicy preservation,
    AskMode ask) {

  public static final SourceUpdateMode DEFAULT_SOURCE_UPDATE = SourceUpdateMode.FIXPOINT;
  public static final int DEFAULT_MAX_ROUNDS = 3;
  public static final MigrationMode DEFAULT_MODE = MigrationMode.ID_DIFF;

  public static MigrationOptions parse(CommandLine commandLine) {
    Path vsumFolder = requiredExistingFolder(commandLine);
    Path specificationsJar = requiredSpecificationsJar(commandLine);
    StrategyChoice strategy = parseStrategy(commandLine);
    Optional<String> dominantToken =
        commandLine.value(CommandLine.DOMINANT, CommandLine.DOMINANT_SHORT);
    if (strategy == StrategyChoice.EXPLICIT && dominantToken.isEmpty()) {
      throw new UsageException(
          "The explicit strategy needs %s/%s <token> to know which metamodel dominates"
              .formatted(CommandLine.DOMINANT, CommandLine.DOMINANT_SHORT));
    }

    return new MigrationOptions(
        vsumFolder,
        specificationsJar,
        strategy,
        dominantToken,
        parseSourceUpdate(commandLine),
        parseMaxRounds(commandLine),
        commandLine.flag(CommandLine.BACKUP, CommandLine.BACKUP_SHORT),
        parseMode(commandLine),
        parsePreservation(commandLine),
        parseAsk(commandLine));
  }

  private static Path requiredExistingFolder(CommandLine commandLine) {
    Path vsumFolder =
        commandLine
            .value(CommandLine.MODEL, CommandLine.MODEL_SHORT)
            .map(Path::of)
            .orElseThrow(
                () ->
                    new UsageException(
                        "Missing required option %s/%s <folder>"
                            .formatted(CommandLine.MODEL, CommandLine.MODEL_SHORT)));
    if (!Files.isDirectory(vsumFolder)) {
      throw new UsageException(
          "Model folder does not exist or is not a directory: " + vsumFolder.toAbsolutePath());
    }

    return vsumFolder;
  }

  private static Path requiredSpecificationsJar(CommandLine commandLine) {
    return commandLine
        .value(CommandLine.PROPAGATIONS, CommandLine.PROPAGATIONS_SHORT)
        .map(Path::of)
        .orElseThrow(
            () ->
                new UsageException(
                    "Missing required option %s/%s <jar> (the usecase jar with the change-propagation specifications)"
                        .formatted(CommandLine.PROPAGATIONS, CommandLine.PROPAGATIONS_SHORT)));
  }

  private static <E> E choice(
      CommandLine commandLine,
      String option,
      String what,
      Function<String, Optional<E>> fromToken,
      List<String> tokens,
      E fallback) {
    return choice(commandLine, option, option, what, fromToken, tokens, fallback);
  }

  private static <E> E choice(
      CommandLine commandLine,
      String longOption,
      String shortOption,
      String what,
      Function<String, Optional<E>> fromToken,
      List<String> tokens,
      E fallback) {
    return commandLine
        .value(longOption, shortOption)
        .map(
            token ->
                fromToken
                    .apply(token)
                    .orElseThrow(
                        () ->
                            new UsageException(
                                "Unknown %s '%s'. Use one of: %s"
                                    .formatted(what, token, String.join(", ", tokens)))))
        .orElse(fallback);
  }

  private static StrategyChoice parseStrategy(CommandLine commandLine) {
    return choice(
        commandLine,
        CommandLine.STRATEGY,
        CommandLine.STRATEGY_SHORT,
        "strategy",
        StrategyChoice::fromToken,
        StrategyChoice.TOKENS,
        StrategyChoice.EXPLICIT);
  }

  private static MigrationMode parseMode(CommandLine commandLine) {
    return choice(
        commandLine,
        CommandLine.MODE,
        "migration mode",
        MigrationMode::fromToken,
        MigrationMode.TOKENS,
        DEFAULT_MODE);
  }

  private static PreservationPolicy parsePreservation(CommandLine commandLine) {
    return choice(
        commandLine,
        CommandLine.PRESERVE,
        "preservation policy",
        PreservationPolicy::fromToken,
        PreservationPolicy.TOKENS,
        PreservationPolicy.USER);
  }

  private static AskMode parseAsk(CommandLine commandLine) {
    return choice(
        commandLine,
        CommandLine.ASK,
        "value for " + CommandLine.ASK,
        AskMode::fromToken,
        AskMode.TOKENS,
        AskMode.AUTO);
  }

  private static SourceUpdateMode parseSourceUpdate(CommandLine commandLine) {
    return choice(
        commandLine,
        CommandLine.SOURCE_UPDATE,
        CommandLine.SOURCE_UPDATE_SHORT,
        "source-update mode",
        SourceUpdateMode::fromToken,
        SourceUpdateMode.TOKENS,
        DEFAULT_SOURCE_UPDATE);
  }

  private static int parseMaxRounds(CommandLine commandLine) {
    return commandLine
        .value(CommandLine.MAX_ROUNDS, CommandLine.MAX_ROUNDS_SHORT)
        .map(MigrationOptions::positiveRounds)
        .orElse(DEFAULT_MAX_ROUNDS);
  }

  private static int positiveRounds(String token) {
    try {
      int rounds = Integer.parseInt(token);
      if (rounds < 1) {
        throw new UsageException("Source-update rounds must be at least 1, got " + rounds);
      }

      return rounds;
    } catch (NumberFormatException e) {
      throw new UsageException(
          "Not a number for %s: '%s'".formatted(CommandLine.MAX_ROUNDS, token));
    }
  }
}
