package tools.vitruv.dsls.reactions.migration.interaction;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import tools.vitruv.change.interaction.InteractionResultProvider;
import tools.vitruv.change.interaction.UserInteractionOptions.InputValidator;
import tools.vitruv.change.interaction.UserInteractionOptions.NotificationType;
import tools.vitruv.change.interaction.UserInteractionOptions.WindowModality;

@Slf4j
public class NonInteractiveDefaults implements InteractionResultProvider {
  @Override
  public boolean getConfirmationInteractionResult(
      WindowModality modality,
      String title,
      String message,
      String positive,
      String negative,
      String cancel) {
    log.warn("Declined a confirmation nobody could answer: {}", message);
    return false;
  }

  @Override
  public void getNotificationInteractionResult(
      WindowModality modality,
      String title,
      String message,
      String positive,
      NotificationType notificationType) {
    log.info("{}", message);
  }

  @Override
  public String getTextInputInteractionResult(
      WindowModality modality,
      String title,
      String message,
      String positive,
      String cancel,
      InputValidator validator) {
    log.warn("Left a text input nobody could answer empty: {}", message);
    return "";
  }

  @Override
  public int getMultipleChoiceSingleSelectionInteractionResult(
      WindowModality modality,
      String title,
      String message,
      String positive,
      String cancel,
      Iterable<String> choices) {
    log.warn("Took the first choice of a selection nobody could answer: {}", message);
    return 0;
  }

  @Override
  public Iterable<Integer> getMultipleChoiceMultipleSelectionInteractionResult(
      WindowModality modality,
      String title,
      String message,
      String positive,
      String cancel,
      Iterable<String> choices) {
    log.warn("Selected nothing where nobody could answer: {}", message);
    return List.of();
  }
}
