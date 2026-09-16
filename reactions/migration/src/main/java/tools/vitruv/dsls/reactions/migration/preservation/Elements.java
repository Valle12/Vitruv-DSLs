package tools.vitruv.dsls.reactions.migration.preservation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;

/** Describes and compares model elements, in the wording the preservation report uses. */
public final class Elements {
  private Elements() {}

  /**
   * Returns the metaclass of the element, qualified by the package it comes from.
   *
   * @param element the element to name
   * @return the metaclass, written as package and class name
   */
  public static String typeOf(EObject element) {
    return element.eClass().getEPackage().getName() + "::" + element.eClass().getName();
  }

  /**
   * Describes the element by its metaclass, its name, the named elements containing it and its
   * location, so that a reader can find it again in the model file.
   *
   * @param element the element to describe
   * @param folder the folder the location is relative to
   * @return the description of the element
   */
  public static String describe(EObject element, Path folder) {
    StringBuilder description = new StringBuilder(typeOf(element));
    nameOf(element).ifPresent(name -> description.append(" '").append(name).append("'"));
    String containers = namedContainersOf(element);
    if (!containers.isEmpty()) {
      description.append(" in ").append(containers);
    }

    String location = locationOf(element, folder);
    return location == null ? description.toString() : description + " at " + location;
  }

  private static String namedContainersOf(EObject element) {
    List<String> names = new ArrayList<>();
    for (EObject container = element.eContainer();
        container != null;
        container = container.eContainer()) {
      Optional<String> name = nameOf(container);
      if (name.isPresent() && !nameRestatesFile(container, name.get())) {
        names.add(name.get());
      }
    }

    Collections.reverse(names);
    return String.join(".", names);
  }

  private static boolean nameRestatesFile(EObject container, String name) {
    if (container.eContainer() != null || container.eResource() == null) {
      return false;
    }

    String file = container.eResource().getURI().lastSegment();
    return file != null && !file.isEmpty() && name.endsWith(file);
  }

  /**
   * Describes what a candidate would carry over, which is the contained element itself for a
   * containment and the feature of its owner otherwise.
   *
   * @param candidate the candidate to describe
   * @param folder the folder the location is relative to
   * @return the description of the candidate
   */
  public static String describe(PreservationCandidate candidate, Path folder) {
    return candidate.isContainment()
        ? describe((EObject) candidate.oldValue(), folder)
        : describe(candidate.oldOwner(), folder) + "." + candidate.oldFeature().getName();
  }

  /**
   * Returns where the element sits, as the model file and the path inside it.
   *
   * @param element the element to locate
   * @param folder the folder the file name is relative to
   * @return the location, or {@code null} when the element is in no resource below the folder
   */
  public static String locationOf(EObject element, Path folder) {
    String resourceKey = ElementPaths.resourceKey(element.eResource(), folder);
    return resourceKey == null ? null : resourceKey + "#" + ElementPaths.pathOf(element);
  }

  /**
   * Returns the name the element carries, if its metaclass gives it one.
   *
   * @param element the element to read
   * @return the value of its single-valued {@code name} attribute, empty when it has none set
   */
  public static Optional<String> nameOf(EObject element) {
    return element.eClass().getEAllAttributes().stream()
        .filter(attribute -> "name".equals(attribute.getName()) && !attribute.isMany())
        .filter(element::eIsSet)
        .map(element::eGet)
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .findFirst();
  }

  /**
   * Returns the metaclass and the attribute values of the element, which is enough to tell two
   * elements apart without following their references.
   *
   * @param element the element to read
   * @return its metaclass followed by one entry per attribute, derived ones left out
   */
  public static List<Object> shallowSignature(EObject element) {
    List<Object> signature = new ArrayList<>();
    signature.add(element.eClass());
    for (EAttribute attribute : element.eClass().getEAllAttributes()) {
      signature.add(attribute.isDerived() ? null : element.eGet(attribute));
    }

    return signature;
  }

  /**
   * Returns the values the element holds in the given feature, as a list whether the feature is
   * many-valued or not.
   *
   * @param element the element to read
   * @param feature the feature to read
   * @return its values, empty when the feature is unset or holds {@code null}
   */
  @SuppressWarnings("unchecked")
  public static List<Object> valuesOf(EObject element, EStructuralFeature feature) {
    if (!element.eIsSet(feature)) {
      return List.of();
    }

    Object value = element.eGet(feature);
    if (feature.isMany()) {
      return new ArrayList<>((List<Object>) value);
    }

    return value == null ? List.of() : List.of(value);
  }

  /**
   * Returns whether a value of the given feature could be carried over at all.
   *
   * @param feature the feature to judge
   * @return whether it is changeable, not derived and not the opposite of a containment
   */
  public static boolean isTransplantable(EStructuralFeature feature) {
    return feature.isChangeable()
        && !feature.isDerived()
        && !(feature instanceof EReference reference && reference.isContainer());
  }

  /**
   * Returns whether both lists hold the same values, without regard to their order.
   *
   * @param left the one list
   * @param right the other list
   * @return whether each value of the one has a match in the other
   */
  public static boolean sameValues(List<Object> left, List<Object> right) {
    if (left.size() != right.size()) {
      return false;
    }

    List<Object> unmatched = new ArrayList<>(right);
    for (Object value : left) {
      int match = indexOfValue(unmatched, value);
      if (match < 0) {
        return false;
      }
      unmatched.remove(match);
    }

    return true;
  }

  private static int indexOfValue(List<Object> values, Object value) {
    for (int index = 0; index < values.size(); index++) {
      if (sameValue(values.get(index), value)) {
        return index;
      }
    }

    return -1;
  }

  /**
   * Returns whether two values count as the same. Two elements do when they are the same object
   * or lie at the same URI, so that a value read twice from a file still compares as equal.
   *
   * @param left the one value
   * @param right the other value
   * @return whether they count as the same
   */
  public static boolean sameValue(Object left, Object right) {
    if (!(left instanceof EObject leftElement) || !(right instanceof EObject rightElement)) {
      return Objects.equals(left, right);
    }

    return leftElement == rightElement
        || Objects.equals(EcoreUtil.getURI(leftElement), EcoreUtil.getURI(rightElement));
  }
}
