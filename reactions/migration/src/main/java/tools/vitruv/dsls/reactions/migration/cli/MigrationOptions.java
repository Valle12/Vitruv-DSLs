package tools.vitruv.dsls.reactions.migration.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import tools.vitruv.dsls.reactions.migration.strategy.StrategyChoice;

public record MigrationOptions(
    Path vsumFolder,
    Path specificationsJar,
    StrategyChoice strategy,
    Optional<String> dominantToken,
    SourceUpdateMode sourceUpdate,
    int maxSourceUpdateRounds,
    boolean backup) {

  public static final SourceUpdateMode DEFAULT_SOURCE_UPDATE = SourceUpdateMode.FIXPOINT;
  public static final int DEFAULT_MAX_ROUNDS = 3;

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
        commandLine.flag(CommandLine.BACKUP, CommandLine.BACKUP_SHORT));
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

  private static StrategyChoice parseStrategy(CommandLine commandLine) {
    return commandLine
        .value(CommandLine.STRATEGY, CommandLine.STRATEGY_SHORT)
        .map(MigrationOptions::strategyFromToken)
        .orElse(StrategyChoice.EXPLICIT);
  }

  private static StrategyChoice strategyFromToken(String token) {
    return StrategyChoice.fromToken(token)
        .orElseThrow(
            () ->
                new UsageException(
                    "Unknown strategy '%s'. Use one of: explicit, reachability, derivationLoss"
                        .formatted(token)));
  }

  private static SourceUpdateMode parseSourceUpdate(CommandLine commandLine) {
    return commandLine
        .value(CommandLine.SOURCE_UPDATE, CommandLine.SOURCE_UPDATE_SHORT)
        .map(MigrationOptions::sourceUpdateFromToken)
        .orElse(DEFAULT_SOURCE_UPDATE);
  }

  private static SourceUpdateMode sourceUpdateFromToken(String token) {
    return SourceUpdateMode.fromToken(token)
        .orElseThrow(
            () ->
                new UsageException(
                    "Unknown source-update mode '%s'. Use one of: none, fixpoint"
                        .formatted(token)));
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
