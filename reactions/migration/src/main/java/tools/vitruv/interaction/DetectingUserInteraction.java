package tools.vitruv.interaction;

import java.util.ArrayList;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.vitruv.change.interaction.InteractionResultProvider;
import tools.vitruv.change.interaction.UserInteractionOptions.InputValidator;
import tools.vitruv.change.interaction.UserInteractionOptions.NotificationType;
import tools.vitruv.change.interaction.UserInteractionOptions.WindowModality;

@Slf4j
@NoArgsConstructor(force = true)
@RequiredArgsConstructor
// TODO maybe empty constructor for no fallback might be needed
public class DetectingUserInteraction implements InteractionResultProvider {
  private static final String CONFIRMATION = "confirmation";
  private static final String TEXT_INPUT = "text input";
  private static final String SINGLE_SELECTION = "single selection";
  private static final String MULTI_SELECTION = "multiple selection";
  private final List<String> requestedInteractions = new ArrayList<>();
  private final InteractionResultProvider fallback;

  public List<String> getRequestedInteractions() {
    return List.copyOf(requestedInteractions);
  }

  private RuntimeException addRequestedInteraction(String kind, String message) {
    String description = "%s: %s".formatted(kind, message);
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
    RuntimeException unanswered = addRequestedInteraction(CONFIRMATION, message);
    if (fallback == null) {
      throw unanswered;
    }

    return fallback.getConfirmationInteractionResult(
        modality, title, message, positive, negative, cancel);
  }

  @Override
  public String getTextInputInteractionResult(
      WindowModality modality,
      String title,
      String message,
      String positive,
      String negative,
      InputValidator validator) {
    RuntimeException unanswered = addRequestedInteraction(TEXT_INPUT, message);
    if (fallback == null) {
      throw unanswered;
    }

    return fallback.getTextInputInteractionResult(
        modality, title, message, positive, negative, validator);
  }

  @Override
  public int getMultipleChoiceSingleSelectionInteractionResult(
      WindowModality modality,
      String title,
      String message,
      String positive,
      String negative,
      Iterable<String> choices) {
    RuntimeException unanswered = addRequestedInteraction(SINGLE_SELECTION, message);
    if (fallback == null) {
      throw unanswered;
    }

    return fallback.getMultipleChoiceSingleSelectionInteractionResult(
        modality, title, message, positive, negative, choices);
  }

  @Override
  public Iterable<Integer> getMultipleChoiceMultipleSelectionInteractionResult(
      WindowModality modality,
      String title,
      String message,
      String positive,
      String negative,
      Iterable<String> choices) {
    RuntimeException unanswered = addRequestedInteraction(MULTI_SELECTION, message);
    if (fallback == null) {
      throw unanswered;
    }

    return fallback.getMultipleChoiceMultipleSelectionInteractionResult(
        modality, title, message, positive, negative, choices);
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
}
