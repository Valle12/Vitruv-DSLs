package tools.vitruv.dsls.reactions.migration.preservation;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * One value of the old state that could be carried over, together with where it would go.
 *
 * @param oldOwner the element that held the value before the migration
 * @param oldFeature the feature it sat in
 * @param oldValue the value itself, an element for a containment and a plain value otherwise
 * @param newOwner the migrated element it would be put on
 * @param targetFeature the feature of that element it would be put into
 * @param kind why it is carried over
 */
public record PreservationCandidate(
    EObject oldOwner,
    EStructuralFeature oldFeature,
    Object oldValue,
    EObject newOwner,
    EStructuralFeature targetFeature,
    Kind kind) {
  /**
   * Creates a candidate that is carried over on its own account rather than to make room for
   * something else.
   *
   * @param oldOwner the element that held the value before the migration
   * @param oldFeature the feature it sat in
   * @param oldValue the value itself
   * @param newOwner the migrated element it would be put on
   * @param targetFeature the feature of that element it would be put into
   */
  public PreservationCandidate(
      EObject oldOwner,
      EStructuralFeature oldFeature,
      Object oldValue,
      EObject newOwner,
      EStructuralFeature targetFeature) {
    this(oldOwner, oldFeature, oldValue, newOwner, targetFeature, Kind.NORMAL);
  }

  /**
   * Returns whether the value is an element the old owner contained.
   *
   * @return whether the old feature is a containment reference
   */
  public boolean isContainment() {
    return oldFeature instanceof EReference reference && reference.isContainment();
  }

  /** Why a value is carried over. */
  public enum Kind {
    /** The value is carried over on its own account. */
    NORMAL,
    /** The value is carried over only so that content below it has somewhere to sit. */
    CARRIER,
    /** The value takes the place of one the new rules derived. */
    OVERWRITE
  }
}
