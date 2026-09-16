package tools.vitruv.dsls.reactions.migration.interaction;

/** Thrown when a reaction asks for an interaction in a run that has nobody to answer it. */
public class UserInteractionRequiredException extends RuntimeException {
  /**
   * Creates the exception.
   *
   * @param message what the reaction asked for
   */
  public UserInteractionRequiredException(String message) {
    super(message);
  }
}
