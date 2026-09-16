package tools.vitruv.dsls.reactions.migration.cli;

/**
 * Thrown when the command line misses a required option or carries a value no option accepts.
 */
public class UsageException extends RuntimeException {
  /**
   * Creates the exception.
   *
   * @param message what is wrong with the command line
   */
  public UsageException(String message) {
    super(message);
  }
}
