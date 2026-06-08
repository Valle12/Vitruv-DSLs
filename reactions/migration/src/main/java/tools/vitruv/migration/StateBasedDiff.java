package tools.vitruv.migration;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.emf.ecore.resource.Resource;
import tools.vitruv.framework.views.changederivation.DefaultStateBasedChangeResolutionStrategy;
import tools.vitruv.framework.views.changederivation.StateBasedChangeResolutionStrategy;

public class StateBasedDiff {
  private final StateBasedChangeResolutionStrategy strategy =
      new DefaultStateBasedChangeResolutionStrategy();

  private Map<String, Resource> getResourcesByFileName(Set<Resource> resources) {
    Map<String, Resource> result = new LinkedHashMap<>();
    for (Resource resource : resources) {
      String name =
          resource.getURI() != null ? resource.getURI().lastSegment() : resource.toString();
      result.put(name, resource);
    }

    return result;
  }

  public long changeCount(Set<Resource> persisted, Set<Resource> regenerated) {
    Map<String, Resource> persistedByName = getResourcesByFileName(persisted);
    Map<String, Resource> regeneratedByName = getResourcesByFileName(regenerated);

    Set<String> names = new LinkedHashSet<>(persistedByName.keySet());
    names.addAll(regeneratedByName.keySet());

    long total = 0;
    for (String name : names) {
      Resource before = persistedByName.get(name);
      Resource after = regeneratedByName.get(name);
      if (before != null && after != null) {
        total += strategy.getChangeSequenceBetween(before, after).getEChanges().size();
      } else if (after == null) {
        total += strategy.getChangeSequenceForDeleted(before).getEChanges().size();
      } else {
        total += strategy.getChangeSequenceForCreated(after).getEChanges().size();
      }
    }

    return total;
  }
}
