package tools.vitruv.dsls.reactions.migration.migration;

import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.eclipse.emf.ecore.EObject;

/**
 * References taken out of a set of models so that their resources can be handled one at a
 * time, kept so that they can be put back once the models are in place again.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class DetachedReferences {
  private final List<Reference> references;

  /** Puts every detached reference back into the feature it was taken from. */
  @SuppressWarnings("unchecked")
  public void reattach() {
    for (Reference reference : references) {
      if (reference.feature().isMany()) {
        ((List<EObject>) reference.holder().eGet(reference.feature())).add(reference.target());
      } else {
        reference.holder().eSet(reference.feature(), reference.target());
      }
    }
  }
}