package tools.vitruv.dsls.reactions.migration.preservation;

import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.util.EcoreUtil.Copier;

final class ReferenceRebinder {
  private final UnaryOperator<EObject> toMigratedElement;
  private final Path oldFolder;
  private final Copier copier;
  private final Map<EObject, EObject> prunedCopies;
  private final Set<EObject> liveCopies;

  ReferenceRebinder(
      UnaryOperator<EObject> toMigratedElement,
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

  boolean rebindFailed(EObject copyRoot) {
    if (rebindElementFailed(copyRoot)) {
      return true;
    }

    Iterator<EObject> it = copyRoot.eAllContents();
    while (it.hasNext()) {
      if (rebindElementFailed(it.next())) {
        return true;
      }
    }

    return false;
  }

  private boolean rebindElementFailed(EObject element) {
    for (EReference reference : element.eClass().getEAllReferences()) {
      if (reference.isContainment() || !Elements.isTransplantable(reference)) {
        continue;
      }

      if (rebindReferenceFailed(element, reference)) {
        return true;
      }
    }

    return false;
  }

  @SuppressWarnings("unchecked")
  private boolean rebindReferenceFailed(EObject element, EReference reference) {
    if (reference.isMany()) {
      return rebindTargetsFailed((List<EObject>) element.eGet(reference));
    }

    if (!(element.eGet(reference) instanceof EObject target)) {
      return false;
    }

    EObject rebound = rebindTarget(target);
    if (rebound == null) {
      return true;
    }

    element.eSet(reference, rebound);
    return false;
  }

  private boolean rebindTargetsFailed(List<EObject> targets) {
    for (ListIterator<EObject> targetsToRebind = targets.listIterator();
        targetsToRebind.hasNext(); ) {
      EObject current = targetsToRebind.next();
      EObject rebound = rebindTarget(current);
      if (rebound == null) {
        return true;
      }

      if (rebound == current) {
        continue;
      }

      if (targets.contains(rebound)) {
        targetsToRebind.remove();
      } else {
        targetsToRebind.set(rebound);
      }
    }

    return false;
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
