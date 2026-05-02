package tools.vitruv.reactions.preprocessor;

import lombok.extern.slf4j.Slf4j;
import tools.vitruv.reactions.preprocessor.extractor.FeatureExtractor;

@Slf4j
public class Main {
  private static final String HELP = "--help";
  private static final String CONFIG = "--config";
  private static final String CONFIG_SHORT = "-c";
  private static final String REACTIONS = "--reactions";
  private static final String REACTIONS_SHORT = "-r";

  public static void main(String[] args) {
    run(args);
  }

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

    boolean configFirst;
    configFirst = CONFIG.equals(args[0]) || CONFIG_SHORT.equals(args[0]);
    String configFile = configFirst ? args[1] : args[3];
    String reactionsDir = configFirst ? args[3] : args[1];

    FeatureExtractor featureExtractor = new FeatureExtractor(configFile, reactionsDir);
    featureExtractor.extractFeatures();
    return 0;
  }
}
