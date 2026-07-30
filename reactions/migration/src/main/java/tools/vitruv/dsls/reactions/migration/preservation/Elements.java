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

public final class Elements {
  private Elements() {}

  public static String typeOf(EObject element) {
    return element.eClass().getEPackage().getName() + "::" + element.eClass().getName();
  }

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

  public static String describe(PreservationCandidate candidate, Path folder) {
    return candidate.isContainment()
        ? describe((EObject) candidate.oldValue(), folder)
        : describe(candidate.oldOwner(), folder) + "." + candidate.oldFeature().getName();
  }

  public static String locationOf(EObject element, Path folder) {
    String resourceKey = ElementPaths.resourceKey(element.eResource(), folder);
    return resourceKey == null ? null : resourceKey + "#" + ElementPaths.pathOf(element);
  }

  public static Optional<String> nameOf(EObject element) {
    return element.eClass().getEAllAttributes().stream()
        .filter(attribute -> "name".equals(attribute.getName()) && !attribute.isMany())
        .filter(element::eIsSet)
        .map(element::eGet)
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .findFirst();
  }

  public static List<Object> shallowSignature(EObject element) {
    List<Object> signature = new ArrayList<>();
    signature.add(element.eClass());
    for (EAttribute attribute : element.eClass().getEAllAttributes()) {
      signature.add(attribute.isDerived() ? null : element.eGet(attribute));
    }

    return signature;
  }

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

  public static boolean isTransplantable(EStructuralFeature feature) {
    return feature.isChangeable()
        && !feature.isDerived()
        && !(feature instanceof EReference reference && reference.isContainer());
  }

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

  public static boolean sameValue(Object left, Object right) {
    if (!(left instanceof EObject leftElement) || !(right instanceof EObject rightElement)) {
      return Objects.equals(left, right);
    }

    return leftElement == rightElement
        || Objects.equals(EcoreUtil.getURI(leftElement), EcoreUtil.getURI(rightElement));
  }
}
