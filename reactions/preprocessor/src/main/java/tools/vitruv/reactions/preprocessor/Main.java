package tools.vitruv.reactions.preprocessor;

import lombok.extern.slf4j.Slf4j;
import tools.vitruv.reactions.preprocessor.extractor.FeatureExtractor;

/**
 * Command line entry point of the preprocessor, which derives the rule set of one configuration
 * from a directory of annotated reactions.
 */
@Slf4j
public class Main {
  private static final String HELP = "--help";
  private static final String CONFIG = "--config";
  private static final String CONFIG_SHORT = "-c";
  private static final String REACTIONS = "--reactions";
  private static final String REACTIONS_SHORT = "-r";

  /**
   * Runs the preprocessor and discards its exit code.
   *
   * @param args the command line arguments, as described by {@link #run(String[])}
   */
  public static void main(String[] args) {
    run(args);
  }

  /**
   * Derives the rule set of one configuration from a directory of annotated reactions. Expects
   * either {@code --help} on its own or exactly four arguments, the configuration file behind
   * {@code --config} or {@code -c} and the reactions directory behind {@code --reactions} or
   * {@code -r}, given in either order.
   *
   * @param args the command line arguments
   * @return 0 when the arguments were accepted, 1 when their number is wrong
   */
  public static int run(String[] args) {
    if (args.length == 1 && HELP.equals(args[0])) {
      log.info(
          "\nUsage: preprocessor [options]\n{}/{}\tConfig file\n{}/{}\tReactions directory",
          CONFIG,
          CONFIG_SHORT,
          REACTIONS,
          REACTIONS_SHORT);
      return 0;
    } else if (args.length != 4) {
      log.error("Expected exactly four arguments, but got {}", args.length);
      return 1;
    }

    boolean configFirst = CONFIG.equals(args[0]) || CONFIG_SHORT.equals(args[0]);
    String configFile = args[configFirst ? 1 : 3];
    String reactionsDir = args[configFirst ? 3 : 1];

    FeatureExtractor featureExtractor = new FeatureExtractor(configFile, reactionsDir);
    featureExtractor.extractFeatures();
    return 0;
  }
}
