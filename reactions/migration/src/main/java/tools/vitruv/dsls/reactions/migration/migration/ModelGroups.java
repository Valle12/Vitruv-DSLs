package tools.vitruv.dsls.reactions.migration.migration;

import java.util.*;
import org.eclipse.emf.ecore.EObject;

final class ModelGroups {
  private ModelGroups() {}

  static Map<String, List<EObject>> byNsUri(Collection<EObject> roots) {
    Map<String, List<EObject>> grouped = new LinkedHashMap<>();
    for (EObject root : roots) {
      grouped
          .computeIfAbsent(root.eClass().getEPackage().getNsURI(), key -> new ArrayList<>())
          .add(root);
    }

    return grouped;
  }
}
