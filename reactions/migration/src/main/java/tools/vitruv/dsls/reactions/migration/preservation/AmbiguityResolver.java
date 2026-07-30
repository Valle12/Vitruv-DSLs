package tools.vitruv.dsls.reactions.migration.preservation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import tools.vitruv.change.interaction.InteractionResultProvider;
import tools.vitruv.change.interaction.UserInteractionOptions.WindowModality;

@Slf4j
public final class AmbiguityResolver {
  private static final String TITLE = "Migration: preserving derived content";
  private static final String NONE = "none of these";
  private static final String KEEP = "Keep";
  private static final String SKIP = "Skip";

  private final Path oldFolder;
  private final Path newFolder;
  private final List<DecisionItem> decisions = new ArrayList<>();
  private final InteractionResultProvider interaction;
  private boolean providerFailed;

  private AmbiguityResolver(InteractionResultProvider interaction, Path oldFolder, Path newFolder) {
    this.interaction = interaction;
    this.oldFolder = oldFolder;
    this.newFolder = newFolder;
  }

  public static AmbiguityResolver asking(
      InteractionResultProvider interaction, Path oldFolder, Path newFolder) {
    return new AmbiguityResolver(interaction, oldFolder, newFolder);
  }

  public static AmbiguityResolver reporting(Path oldFolder, Path newFolder) {
    return new AmbiguityResolver(null, oldFolder, newFolder);
  }

  private static String describeFeature(EStructuralFeature feature) {
    return "%s : %s".formatted(feature.getName(), feature.getEType().getName());
  }

  public boolean resolves() {
    return interaction != null;
  }

  private boolean canAsk() {
    return interaction != null && !providerFailed;
  }

  public List<DecisionItem> decisions() {
    return List.copyOf(decisions);
  }

  Optional<EObject> chooseCounterpart(EObject oldElement, List<EObject> candidates) {
    if (candidates.isEmpty()) {
      return Optional.empty();
    }

    String question =
        "Which migrated element continues %s?".formatted(Elements.describe(oldElement, oldFolder));
    List<String> choices = candidates.stream().map(this::describeMigrated).toList();
    return select(question, choices).map(candidates::get);
  }

  Optional<EStructuralFeature> chooseFeature(
      String subject, EObject newOwner, List<EStructuralFeature> alternatives) {
    if (alternatives.isEmpty()) {
      return Optional.empty();
    }

    String question =
        "Which feature of %s should hold %s?".formatted(Elements.typeOf(newOwner), subject);
    List<String> choices = alternatives.stream().map(AmbiguityResolver::describeFeature).toList();
    return select(question, choices).map(alternatives::get);
  }

  private Optional<Integer> select(String question, List<String> choices) {
    boolean asked = canAsk();
    Optional<Integer> chosen = asked ? ask(question, choices) : Optional.empty();
    if (chosen.isPresent()) {
      decisions.add(DecisionItem.answered(question, choices.get(chosen.get())));
    } else if (asked && !providerFailed) {
      decisions.add(DecisionItem.answered(question, NONE));
    } else {
      decisions.add(DecisionItem.unresolved(question, String.join(", ", choices)));
    }

    return chosen;
  }

  private Optional<Integer> ask(String question, List<String> choices) {
    List<String> offered = new ArrayList<>(choices);
    offered.add(NONE);
    try {
      int chosen =
          interaction.getMultipleChoiceSingleSelectionInteractionResult(
              WindowModality.MODAL, TITLE, question, KEEP, SKIP, offered);
      return chosen >= 0 && chosen < choices.size() ? Optional.of(chosen) : Optional.empty();
    } catch (RuntimeException noInteractionPossible) {
      log.warn(
          "Cannot ask which element to keep; the remaining ambiguities are only reported.",
          noInteractionPossible);
      providerFailed = true;
      return Optional.empty();
    }
  }

  private String describeMigrated(EObject element) {
    return Elements.describe(element, newFolder);
  }
}
