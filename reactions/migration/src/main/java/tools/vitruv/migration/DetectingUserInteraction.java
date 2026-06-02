package tools.vitruv.migration;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import tools.vitruv.change.interaction.InteractionResultProvider;
import tools.vitruv.change.interaction.UserInteractionOptions.InputValidator;
import tools.vitruv.change.interaction.UserInteractionOptions.NotificationType;
import tools.vitruv.change.interaction.UserInteractionOptions.WindowModality;

@Slf4j
public final class DetectingUserInteraction implements InteractionResultProvider {
  private static final String CONFIRMATION = "confirmation";
  private static final String TEXT_INPUT = "text input";
  private static final String SINGLE_SELECTION = "single selection";
  private static final String MULTI_SELECTION = "multiple selection";
  private final List<String> requestedInteractions = new ArrayList<>();

  public List<String> getRequestedInteractions() {
    return List.copyOf(requestedInteractions);
  }

  private RuntimeException require(String kind, String message) {
    String description = kind + ": " + message;
    requestedInteractions.add(description);
    return new UserInteractionRequiredException(
        "A reaction requires user interaction (" + description + ")");
  }

  @Override
  public boolean getConfirmationInteractionResult(
      WindowModality modality,
      String title,
      String message,
      String positive,
      String negative,
      String cancel) {
    throw require(CONFIRMATION, message);
  }

  @Override
  public String getTextInputInteractionResult(
      WindowModality modality,
      String title,
      String message,
      String positive,
      String negative,
      InputValidator validator) {
    throw require(TEXT_INPUT, message);
  }

  @Override
  public int getMultipleChoiceSingleSelectionInteractionResult(
      WindowModality modality,
      String title,
      String message,
      String positive,
      String negative,
      Iterable<String> choices) {
    throw require(SINGLE_SELECTION, message);
  }

  @Override
  public Iterable<Integer> getMultipleChoiceMultipleSelectionInteractionResult(
      WindowModality modality,
      String title,
      String message,
      String positive,
      String negative,
      Iterable<String> choices) {
    throw require(MULTI_SELECTION, message);
  }

  @Override
  public void getNotificationInteractionResult(
      WindowModality modality,
      String title,
      String message,
      String positive,
      NotificationType type) {
    log.info("Reaction notification [{}]: {}", type, message);
  }

  public static final class UserInteractionRequiredException extends RuntimeException {
    UserInteractionRequiredException(String message) {
      super(message);
    }
  }
}
