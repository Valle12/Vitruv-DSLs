package tools.vitruv.cli;

import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class CLI {
  public static final String HELP = "--help";
  public static final String HELP_SHORT = "-h";
  public static final String MODEL = "--model";
  public static final String MODEL_SHORT = "-m";
  public static final String BACKUP = "--backup";
  public static final String BACKUP_SHORT = "-b";
  public static final String STRATEGY = "--strategy";
  public static final String STRATEGY_SHORT = "-s";
  public static final String DOMINANT = "--dominant";
  public static final String DOMINANT_SHORT = "-d";
  public static final String PROPAGATIONS = "--propagations";
  public static final String PROPAGATIONS_SHORT = "-p";
  public static final String DEFAULT_STRATEGY = "explicit";
  public static final String DEFAULT_DOMINANT = "uml";
  private final String[] args;

  public boolean isHelpCommand() {
    return hasFlag(HELP, HELP_SHORT);
  }

  public void printUsage() {
    log.info(
"""
Usage: migration {}/{} <folder> [{}/{} <jar>] [{}/{} <explicit|reachability|derivationLoss>]\
[{}/{} <token>] [{}/{}]
{}/{}\tPath to the persisted VSUM folder to migrate in place
{}/{}\tJar with the usecases' change-propagation specifications (default: built-in UML<->Java)
{}/{}\tDominance strategy (default: {})
{}/{}\tFor the explicit strategy, the dominant metamodel token, e.g. uml or java (default: {})
{}/{}\tCopy the folder to a timestamped backup before migrating""",
        MODEL,
        MODEL_SHORT,
        PROPAGATIONS,
        PROPAGATIONS_SHORT,
        STRATEGY,
        STRATEGY_SHORT,
        DOMINANT,
        DOMINANT_SHORT,
        BACKUP,
        BACKUP_SHORT,
        MODEL,
        MODEL_SHORT,
        PROPAGATIONS,
        PROPAGATIONS_SHORT,
        STRATEGY,
        STRATEGY_SHORT,
        DEFAULT_STRATEGY,
        DOMINANT,
        DOMINANT_SHORT,
        DEFAULT_DOMINANT,
        BACKUP,
        BACKUP_SHORT);
  }

  public Optional<String> getOptionValue(String longName, String shortName) {
    for (int i = 0; i < args.length - 1; i++) {
      if (longName.equals(args[i]) || shortName.equals(args[i])) {
        return Optional.of(args[i + 1]);
      }
    }

    return Optional.empty();
  }

  public boolean hasFlag(String longName, String shortName) {
    return Arrays.stream(args).anyMatch(arg -> longName.equals(arg) || shortName.equals(arg));
  }
}
