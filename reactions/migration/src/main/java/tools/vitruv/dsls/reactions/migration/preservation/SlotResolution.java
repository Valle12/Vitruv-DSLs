package tools.vitruv.dsls.reactions.migration.preservation;

import java.util.List;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * What the search for a place to put a value came to.
 *
 * @param slot where the value may go, {@code null} when no place was found
 * @param reason why no place was found, {@code null} when one was
 * @param detail what exactly stood in the way
 * @param alternatives the features that could all have held the value, when several could
 */
public record SlotResolution(
    TargetSlot slot, LossReason reason, String detail, List<EStructuralFeature> alternatives) {
  /**
   * Returns a resolution naming the feature the value may go into.
   *
   * @param owner the migrated element to put the value on
   * @param feature the feature of that element to put it into
   * @return the resolved slot
   */
  public static SlotResolution of(EObject owner, EStructuralFeature feature) {
    return new SlotResolution(new TargetSlot(owner, feature), null, null, List.of());
  }

  /**
   * Returns a resolution that found no place for the value.
   *
   * @param reason why no feature can hold it
   * @param detail what exactly stood in the way
   * @return the rejected resolution
   */
  public static SlotResolution rejected(LossReason reason, String detail) {
    return new SlotResolution(null, reason, detail, List.of());
  }

  /**
   * Returns a resolution that found more than one feature able to hold the value, which nobody
   * has chosen between yet.
   *
   * @param detail what exactly is ambiguous
   * @param alternatives the features that could all hold the value
   * @return the ambiguous resolution
   */
  public static SlotResolution ambiguous(String detail, List<EStructuralFeature> alternatives) {
    return new SlotResolution(
        null, LossReason.AMBIGUOUS_FEATURE, detail, List.copyOf(alternatives));
  }

  /**
   * Returns whether a place was found.
   *
   * @return whether the resolution names a slot
   */
  public boolean isResolved() {
    return slot != null;
  }
}
