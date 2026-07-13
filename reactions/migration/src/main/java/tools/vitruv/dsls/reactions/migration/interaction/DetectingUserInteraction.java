package tools.vitruv.dsls.reactions.migration.interaction;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import tools.vitruv.change.interaction.InteractionResultProvider;
import tools.vitruv.change.interaction.UserInteractionOptions.InputValidator;
import tools.vitruv.change.interaction.UserInteractionOptions.NotificationType;
import tools.vitruv.change.interaction.UserInteractionOptions.WindowModality;

@Slf4j
public class DetectingUserInteraction implements InteractionResultProvider {
  private static final String CONFIRMATION = "confirmation";
  private static final String TEXT_INPUT = "text input";
  private static final String SINGLE_SELECTION = "single selection";
  private static final String MULTI_SELECTION = "multiple selection";
  private final List<String> requestedInteractions = new ArrayList<>();
  private final InteractionResultProvider fallback;

  public DetectingUserInteraction() {
    this(null);
  }

  public DetectingUserInteraction(InteractionResultProvider fallback) {
    this.fallback = fallback;
  }

  public List<String> getRequestedInteractions() {
    return List.copyOf(requestedInteractions);
  }

  @Override
  public boolean getConfirmationInteractionResult(
      WindowModality modality,
      String title,
      String message,
      String positive,
      String negative,
      String cancel) {
    failWithoutFallback(CONFIRMATION, message);
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
    failWithoutFallback(TEXT_INPUT, message);
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
    failWithoutFallback(SINGLE_SELECTION, message);
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
    failWithoutFallback(MULTI_SELECTION, message);
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

  private void failWithoutFallback(String kind, String message) {
    String description = "%s: %s".formatted(kind, message);
    requestedInteractions.add(description);
    if (fallback == null) {
      throw new UserInteractionRequiredException(
          "A reaction requires user interaction (" + description + ")");
    }
  }
}
