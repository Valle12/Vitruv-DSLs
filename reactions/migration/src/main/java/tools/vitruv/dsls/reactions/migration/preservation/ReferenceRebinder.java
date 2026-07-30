package tools.vitruv.dsls.reactions.migration.preservation;

import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.util.EcoreUtil.Copier;

final class ReferenceRebinder {
  private final Function<EObject, EObject> toMigratedElement;
  private final Path oldFolder;
  private final Copier copier;
  private final Map<EObject, EObject> prunedCopies;
  private final Set<EObject> liveCopies;

  ReferenceRebinder(
      Function<EObject, EObject> toMigratedElement,
      Path oldFolder,
      Copier copier,
      Map<EObject, EObject> prunedCopies) {
    this.toMigratedElement = toMigratedElement;
    this.oldFolder = oldFolder;
    this.copier = copier;
    this.prunedCopies = prunedCopies;
    this.liveCopies = Collections.newSetFromMap(new IdentityHashMap<>());
    this.liveCopies.addAll(copier.values());
    this.liveCopies.removeAll(prunedCopies.keySet());
  }

  boolean rebind(EObject copyRoot) {
    if (!rebindElement(copyRoot)) {
      return false;
    }

    Iterator<EObject> it = copyRoot.eAllContents();
    while (it.hasNext()) {
      if (!rebindElement(it.next())) {
        return false;
      }
    }

    return true;
  }

  @SuppressWarnings("unchecked")
  private boolean rebindElement(EObject element) {
    for (EReference reference : element.eClass().getEAllReferences()) {
      if (reference.isContainment() || !Elements.isTransplantable(reference)) {
        continue;
      }

      if (reference.isMany()) {
        List<EObject> targets = (List<EObject>) element.eGet(reference);
        for (int i = 0; i < targets.size(); i++) {
          EObject rebound = rebindTarget(targets.get(i));
          if (rebound == null) {
            return false;
          }

          targets.set(i, rebound);
        }
      } else if (element.eGet(reference) instanceof EObject target) {
        EObject rebound = rebindTarget(target);
        if (rebound == null) {
          return false;
        }

        element.eSet(reference, rebound);
      }
    }

    return true;
  }

  EObject rebindTarget(EObject target) {
    if (liveCopies.contains(target)) {
      return target;
    }

    EObject prunedOriginal = prunedCopies.get(target);
    if (prunedOriginal != null) {
      return toMigratedElement.apply(prunedOriginal);
    }

    EObject copy = copier.get(target);
    if (copy != null && liveCopies.contains(copy)) {
      return copy;
    }

    EObject counterpart = toMigratedElement.apply(target);
    if (counterpart != null) {
      return counterpart;
    }

    return ElementPaths.resourceKey(target.eResource(), oldFolder) == null ? target : null;
  }
}
