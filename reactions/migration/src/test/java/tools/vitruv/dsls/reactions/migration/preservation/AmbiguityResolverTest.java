package tools.vitruv.dsls.reactions.migration.preservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.eclipse.emf.ecore.EObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pcm_mockup.PInterface;
import pcm_mockup.Pcm_mockupFactory;
import tools.vitruv.change.interaction.InteractionResultProvider;
import tools.vitruv.change.interaction.UserInteractionOptions.InputValidator;
import tools.vitruv.change.interaction.UserInteractionOptions.NotificationType;
import tools.vitruv.change.interaction.UserInteractionOptions.WindowModality;

class AmbiguityResolverTest {
  private static final Path FOLDER = Path.of("vsum");

  private static PInterface pInterface(String name) {
    PInterface pInterface = Pcm_mockupFactory.eINSTANCE.createPInterface();
    pInterface.setName(name);
    return pInterface;
  }

  private static Optional<EObject> askOnce(AmbiguityResolver resolver) {
    return resolver.chooseCounterpart(pInterface("old"), List.of(pInterface("a"), pInterface("b")));
  }

  @Test
  @DisplayName("a reporting resolver answers nothing and says so")
  void test1() {
    AmbiguityResolver resolver = AmbiguityResolver.reporting(FOLDER, FOLDER);

    assertFalse(resolver.resolves());
    assertTrue(askOnce(resolver).isEmpty());
    assertEquals(
        List.of(DecisionItem.Kind.UNRESOLVED),
        resolver.decisions().stream().map(DecisionItem::kind).toList());
  }

  @Test
  @DisplayName("an asking resolver records the choice it was given")
  void test2() {
    AmbiguityResolver resolver =
        AmbiguityResolver.asking(new StubProvider(question -> 0), FOLDER, FOLDER);

    assertTrue(resolver.resolves());
    assertTrue(askOnce(resolver).isPresent());
    assertEquals(
        List.of(DecisionItem.Kind.ANSWERED),
        resolver.decisions().stream().map(DecisionItem::kind).toList());
  }

  @Test
  @DisplayName("a provider that breaks half-way does not change how later ambiguities are treated")
  void test3() {
    StubProvider provider = new StubProvider(question -> 0);
    AmbiguityResolver resolver = AmbiguityResolver.asking(provider, FOLDER, FOLDER);

    assertTrue(askOnce(resolver).isPresent(), "the first ambiguity is answered");
    provider.breakNow();
    assertTrue(askOnce(resolver).isEmpty(), "the second cannot be");

    assertTrue(
        resolver.resolves(),
        "resolves() describes how the resolver was built, so the collector keeps treating"
            + " unanswered ambiguities the same way for the rest of the run");
    assertEquals(
        List.of(DecisionItem.Kind.ANSWERED, DecisionItem.Kind.UNRESOLVED),
        resolver.decisions().stream().map(DecisionItem::kind).toList(),
        "and the one nobody could answer is reported as such, not as a chosen 'none of these'");
  }

  @Test
  @DisplayName("once the provider broke it is not asked again")
  void test4() {
    StubProvider provider = new StubProvider(question -> 0);
    AmbiguityResolver resolver = AmbiguityResolver.asking(provider, FOLDER, FOLDER);
    provider.breakNow();

    askOnce(resolver);
    askOnce(resolver);

    assertEquals(1, provider.calls, "the second ambiguity must not retry a provider known broken");
  }

  private static final class StubProvider implements InteractionResultProvider {
    private final java.util.function.ToIntFunction<String> answer;
    private int calls;
    private boolean broken;

    private StubProvider(java.util.function.ToIntFunction<String> answer) {
      this.answer = answer;
    }

    private void breakNow() {
      broken = true;
    }

    @Override
    public int getMultipleChoiceSingleSelectionInteractionResult(
        WindowModality modality,
        String title,
        String message,
        String positive,
        String negative,
        Iterable<String> choices) {
      calls++;
      if (broken) {
        throw new IllegalStateException("no console");
      }

      return answer.applyAsInt(message);
    }

    @Override
    public boolean getConfirmationInteractionResult(
        WindowModality modality,
        String title,
        String message,
        String positive,
        String negative,
        String cancel) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void getNotificationInteractionResult(
        WindowModality modality, String title, String message, String ok, NotificationType type) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getTextInputInteractionResult(
        WindowModality modality,
        String title,
        String message,
        String ok,
        String cancel,
        InputValidator validator) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Iterable<Integer> getMultipleChoiceMultipleSelectionInteractionResult(
        WindowModality modality,
        String title,
        String message,
        String positive,
        String negative,
        Iterable<String> choices) {
      throw new UnsupportedOperationException();
    }
  }
}
