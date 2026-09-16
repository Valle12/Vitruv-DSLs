package tools.vitruv.dsls.reactions.migration.interaction;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import tools.vitruv.change.interaction.InteractionResultProvider;
import tools.vitruv.change.interaction.UserInteractionOptions.InputValidator;
import tools.vitruv.change.interaction.UserInteractionOptions.NotificationType;
import tools.vitruv.change.interaction.UserInteractionOptions.WindowModality;

/**
 * Records every interaction a reaction asks the caller for while the migration runs. Without a
 * fallback the first request ends the migration, with one the request is recorded and then
 * handed on, so that a run can report what it was asked even when it kept going.
 */
@Slf4j
public class DetectingUserInteraction implements InteractionResultProvider {
  private static final String CONFIRMATION = "confirmation";
  private static final String TEXT_INPUT = "text input";
  private static final String SINGLE_SELECTION = "single selection";
  private static final String MULTI_SELECTION = "multiple selection";
  private final List<String> requestedInteractions = new ArrayList<>();
  private final InteractionResultProvider fallback;

  /** Creates a provider that records a request and then ends the migration with it. */
  public DetectingUserInteraction() {
    this(null);
  }

  /**
   * Creates a provider that records a request and then lets the given fallback answer it.
   *
   * @param fallback the provider answering the request, or {@code null} to end the migration on
   *     the first one
   */
  public DetectingUserInteraction(InteractionResultProvider fallback) {
    this.fallback = fallback;
  }

  /**
   * Returns what the reactions have asked for so far.
   *
   * @return one description per request, in the order the requests were made
   */
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
