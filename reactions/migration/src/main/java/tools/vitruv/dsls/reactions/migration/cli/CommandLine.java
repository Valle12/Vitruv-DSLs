package tools.vitruv.dsls.reactions.migration.cli;

import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CommandLine {
  public static final String HELP = "--help";
  public static final String HELP_SHORT = "-h";
  public static final String MODEL = "--model";
  public static final String MODEL_SHORT = "-m";
  public static final String PROPAGATIONS = "--propagations";
  public static final String PROPAGATIONS_SHORT = "-p";
  public static final String STRATEGY = "--strategy";
  public static final String STRATEGY_SHORT = "-s";
  public static final String DOMINANT = "--dominant";
  public static final String DOMINANT_SHORT = "-d";
  public static final String SOURCE_UPDATE = "--source-update";
  public static final String SOURCE_UPDATE_SHORT = "-u";
  public static final String MAX_ROUNDS = "--max-rounds";
  public static final String MAX_ROUNDS_SHORT = "-r";
  public static final String BACKUP = "--backup";
  public static final String BACKUP_SHORT = "-b";
  private final String[] args;

  public boolean isHelpRequested() {
    return flag(HELP, HELP_SHORT);
  }

  public Optional<String> value(String longName, String shortName) {
    for (int i = 0; i < args.length - 1; i++) {
      if (longName.equals(args[i]) || shortName.equals(args[i])) {
        return Optional.of(args[i + 1]);
      }
    }

    return Optional.empty();
  }

  public boolean flag(String longName, String shortName) {
    return Arrays.stream(args).anyMatch(arg -> longName.equals(arg) || shortName.equals(arg));
  }

  public static String usage() {
    return """
        Usage: migration --model/-m <folder> --propagations/-p <jar> [options]
          --model/-m <folder>          Path to the persisted VSUM folder to migrate in place
          --propagations/-p <jar>      Jar with the new change-propagation specifications
          --strategy/-s <name>         Dominance strategy: explicit | reachability | derivationLoss
                                       (alias fewestChanges); default: explicit
          --dominant/-d <token>        For the explicit strategy: the dominant metamodel token,
                                       e.g. uml or java (required iff strategy=explicit)
          --source-update/-u <mode>    none | fixpoint; default: fixpoint (update the sources with
                                       information only present in the old derived models)
          --max-rounds/-r <n>          Maximum source-update rounds; default: 3
          --backup/-b                  Copy the folder to a timestamped backup before migrating
          --help/-h                    Print this help""";
  }
}
