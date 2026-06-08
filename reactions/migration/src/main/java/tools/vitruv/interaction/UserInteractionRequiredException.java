package tools.vitruv.interaction;

public class UserInteractionRequiredException extends RuntimeException {
  public UserInteractionRequiredException(String message) {
    super(message);
  }
}
