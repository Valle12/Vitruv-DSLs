package tools.vitruv.dsls.reactions.migration.preservation;

import java.util.List;
import java.util.Map;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * Looks for a feature of a migrated element that can hold a value of the old state. A feature
 * of the same name is preferred, and where several others would do, the choice is left open
 * rather than made arbitrarily.
 */
public final class SlotResolver {
  private static final Map<Class<?>, Class<?>> WRAPPERS =
      Map.of(
          boolean.class, Boolean.class,
          byte.class, Byte.class,
          char.class, Character.class,
          short.class, Short.class,
          int.class, Integer.class,
          long.class, Long.class,
          float.class, Float.class,
          double.class, Double.class);

  private SlotResolver() {}

  /**
   * Looks for the feature of the new owner that can hold the given value.
   *
   * @param newOwner the migrated element the value is to be put on
   * @param oldFeature the feature the value sat in before the migration
   * @param value the value to place
   * @return the feature found, or why none was found and which ones would have done
   */
  public static SlotResolution findSlot(
      EObject newOwner, EStructuralFeature oldFeature, Object value) {
    EStructuralFeature sameName = newOwner.eClass().getEStructuralFeature(oldFeature.getName());
    if (accepts(sameName, oldFeature, value)) {
      return SlotResolution.of(newOwner, sameName);
    }

    List<EStructuralFeature> compatible =
        newOwner.eClass().getEAllStructuralFeatures().stream()
            .filter(feature -> accepts(feature, oldFeature, value))
            .toList();
    if (compatible.isEmpty()) {
      return SlotResolution.rejected(
          LossReason.NO_COMPATIBLE_FEATURE,
          "%s has no %s feature accepting %s"
              .formatted(Elements.typeOf(newOwner), kindOf(oldFeature), describeValue(value)));
    }

    if (compatible.size() > 1) {
      return SlotResolution.ambiguous(
          "%s could hold %s in %s"
              .formatted(
                  Elements.typeOf(newOwner),
                  describeValue(value),
                  compatible.stream().map(EStructuralFeature::getName).toList()),
          compatible);
    }

    return SlotResolution.of(newOwner, compatible.getFirst());
  }

  /**
   * Returns whether the given slot can take one more value.
   *
   * @param slot the feature of a migrated element
   * @return the same slot when it has room, and otherwise why it has none
   */
  public static SlotResolution checkCapacity(TargetSlot slot) {
    EObject newOwner = slot.owner();
    EStructuralFeature feature = slot.feature();
    if (!feature.isMany()) {
      return newOwner.eIsSet(feature)
          ? SlotResolution.rejected(
              LossReason.SLOT_OCCUPIED,
              "%s.%s already holds %s"
                  .formatted(
                      Elements.typeOf(newOwner),
                      feature.getName(),
                      describeValue(newOwner.eGet(feature))))
          : SlotResolution.of(newOwner, feature);
    }

    int upperBound = feature.getUpperBound();
    if (upperBound >= 0 && ((List<?>) newOwner.eGet(feature)).size() >= upperBound) {
      return SlotResolution.rejected(
          LossReason.UPPER_BOUND_REACHED,
          "%s.%s is limited to %d value(s)"
              .formatted(Elements.typeOf(newOwner), feature.getName(), upperBound));
    }

    return SlotResolution.of(newOwner, feature);
  }

  /**
   * Returns whether the given slot already holds the given value, so that carrying it over
   * would change nothing.
   *
   * @param slot the feature of a migrated element
   * @param value the value to look for
   * @return whether the slot already holds it
   */
  public static boolean holds(TargetSlot slot, Object value) {
    EStructuralFeature feature = slot.feature();
    if (feature.isMany()) {
      return ((List<?>) slot.owner().eGet(feature))
          .stream().anyMatch(present -> Elements.sameValue(present, value));
    }

    return slot.owner().eIsSet(feature) && Elements.sameValue(slot.owner().eGet(feature), value);
  }

  private static boolean accepts(
      EStructuralFeature feature, EStructuralFeature oldFeature, Object value) {
    return feature != null
        && Elements.isTransplantable(feature)
        && sameKind(feature, oldFeature)
        && typeAccepts(feature, value);
  }

  private static boolean sameKind(EStructuralFeature feature, EStructuralFeature oldFeature) {
    if (feature instanceof EReference reference && oldFeature instanceof EReference oldReference) {
      return reference.isContainment() == oldReference.isContainment();
    }

    return feature instanceof EAttribute && oldFeature instanceof EAttribute;
  }

  private static boolean typeAccepts(EStructuralFeature feature, Object value) {
    if (feature instanceof EReference reference) {
      return value instanceof EObject element && reference.getEReferenceType().isInstance(element);
    }

    EClassifier type = feature.getEType();
    Class<?> instanceClass = type.getInstanceClass();
    if (instanceClass == null) {
      return value == null || type.isInstance(value);
    }

    return WRAPPERS.getOrDefault(instanceClass, instanceClass).isInstance(value);
  }

  private static String kindOf(EStructuralFeature feature) {
    if (feature instanceof EReference reference) {
      return reference.isContainment() ? "containment" : "reference";
    }

    return "attribute";
  }

  private static String describeValue(Object value) {
    return value instanceof EObject element ? Elements.typeOf(element) : String.valueOf(value);
  }
}
