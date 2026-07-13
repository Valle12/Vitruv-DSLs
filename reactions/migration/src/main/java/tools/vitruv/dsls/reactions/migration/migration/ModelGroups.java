package tools.vitruv.dsls.reactions.migration.migration;

import java.util.*;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;

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

  static Set<Resource> resourcesOf(Collection<EObject> roots) {
    Set<Resource> resources = new LinkedHashSet<>();
    for (EObject root : roots) {
      if (root.eResource() != null) {
        resources.add(root.eResource());
      }
    }

    return resources;
  }
}
