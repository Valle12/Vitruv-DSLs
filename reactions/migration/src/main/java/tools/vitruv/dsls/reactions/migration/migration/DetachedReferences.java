package tools.vitruv.dsls.reactions.migration.migration;

import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.eclipse.emf.ecore.EObject;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class DetachedReferences {
  private final List<Reference> references;

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