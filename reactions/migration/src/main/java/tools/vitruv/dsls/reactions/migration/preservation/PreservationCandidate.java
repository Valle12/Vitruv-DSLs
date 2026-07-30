package tools.vitruv.dsls.reactions.migration.preservation;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;

public record PreservationCandidate(
    EObject oldOwner,
    EStructuralFeature oldFeature,
    Object oldValue,
    EObject newOwner,
    EStructuralFeature targetFeature,
    Kind kind) {
  public PreservationCandidate(
      EObject oldOwner,
      EStructuralFeature oldFeature,
      Object oldValue,
      EObject newOwner,
      EStructuralFeature targetFeature) {
    this(oldOwner, oldFeature, oldValue, newOwner, targetFeature, Kind.NORMAL);
  }

  public boolean isContainment() {
    return oldFeature instanceof EReference reference && reference.isContainment();
  }

  public enum Kind {
    NORMAL,
    CARRIER,
    OVERWRITE
  }
}
