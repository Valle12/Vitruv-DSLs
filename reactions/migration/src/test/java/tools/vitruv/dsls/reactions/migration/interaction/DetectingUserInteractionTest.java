package tools.vitruv.dsls.reactions.migration.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.vitruv.change.interaction.InteractionResultProvider;
import tools.vitruv.change.interaction.UserInteractionOptions.InputValidator;
import tools.vitruv.change.interaction.UserInteractionOptions.NotificationType;
import tools.vitruv.change.interaction.UserInteractionOptions.WindowModality;

class DetectingUserInteractionTest {
  private static final InteractionResultProvider MOCK_ANSWERS =
      new InteractionResultProvider() {
        @Override
        public boolean getConfirmationInteractionResult(
            WindowModality modality,
            String title,
            String message,
            String positive,
            String negative,
            String cancel) {
          return true;
        }

        @Override
        public String getTextInputInteractionResult(
            WindowModality modality,
            String title,
            String message,
            String positive,
            String negative,
            InputValidator validator) {
          return "mock";
        }

        @Override
        public int getMultipleChoiceSingleSelectionInteractionResult(
            WindowModality modality,
            String title,
            String message,
            String positive,
            String negative,
            Iterable<String> choices) {
          return 0;
        }

        @Override
        public Iterable<Integer> getMultipleChoiceMultipleSelectionInteractionResult(
            WindowModality modality,
            String title,
            String message,
            String positive,
            String negative,
            Iterable<String> choices) {
          return List.of(0);
        }

        @Override
        public void getNotificationInteractionResult(
            WindowModality modality,
            String title,
            String message,
            String positive,
            NotificationType type) {
          throw new UnsupportedOperationException(
              "notifications are logged by DetectingUserInteraction and never reach the fallback");
        }
      };

  @Test
  @DisplayName("without fallback every request throws and is recorded")
  void test1() {
    DetectingUserInteraction interaction = new DetectingUserInteraction();

    assertThrows(
        UserInteractionRequiredException.class,
        () ->
            interaction.getConfirmationInteractionResult(
                WindowModality.MODAL, "t", "delete everything?", "yes", "no", "cancel"));
    assertThrows(
        UserInteractionRequiredException.class,
        () ->
            interaction.getTextInputInteractionResult(
                WindowModality.MODAL, "t", "name?", "ok", "cancel", null));

    List<String> requested = interaction.getRequestedInteractions();
    assertEquals(2, requested.size());
    assertTrue(requested.get(0).contains("delete everything?"));
    assertTrue(requested.get(1).contains("name?"));
  }

  @Test
  @DisplayName("with fallback requests are answered and still recorded")
  void test2() {
    DetectingUserInteraction interaction = new DetectingUserInteraction(MOCK_ANSWERS);

    boolean confirmed =
        interaction.getConfirmationInteractionResult(
            WindowModality.MODAL, "t", "proceed?", "yes", "no", "cancel");
    String text =
        interaction.getTextInputInteractionResult(
            WindowModality.MODAL, "t", "name?", "ok", "cancel", null);
    int selection =
        interaction.getMultipleChoiceSingleSelectionInteractionResult(
            WindowModality.MODAL, "t", "pick one", "ok", "cancel", List.of("a", "b"));

    assertTrue(confirmed);
    assertEquals("mock", text);
    assertEquals(0, selection);
    assertEquals(3, interaction.getRequestedInteractions().size());
  }

  @Test
  @DisplayName("notifications are logged, not recorded, and never throw")
  void test3() {
    DetectingUserInteraction interaction = new DetectingUserInteraction();

    interaction.getNotificationInteractionResult(
        WindowModality.MODAL, "t", "for your information", "ok", NotificationType.INFORMATION);

    assertTrue(interaction.getRequestedInteractions().isEmpty());
  }
}
