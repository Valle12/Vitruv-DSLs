package tools.vitruv.dsls.reactions.migration.preservation;

import java.util.List;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

public record SlotResolution(
    TargetSlot slot, LossReason reason, String detail, List<EStructuralFeature> alternatives) {
  public static SlotResolution of(EObject owner, EStructuralFeature feature) {
    return new SlotResolution(new TargetSlot(owner, feature), null, null, List.of());
  }

  public static SlotResolution rejected(LossReason reason, String detail) {
    return new SlotResolution(null, reason, detail, List.of());
  }

  public static SlotResolution ambiguous(String detail, List<EStructuralFeature> alternatives) {
    return new SlotResolution(
        null, LossReason.AMBIGUOUS_FEATURE, detail, List.copyOf(alternatives));
  }

  public boolean isResolved() {
    return slot != null;
  }
}
