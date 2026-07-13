package tools.vitruv.dsls.reactions.migration.migration;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.eclipse.emf.ecore.resource.Resource;
import tools.vitruv.change.atomic.hid.HierarchicalId;
import tools.vitruv.change.composite.description.VitruviusChange;
import tools.vitruv.framework.views.changederivation.DefaultStateBasedChangeResolutionStrategy;
import tools.vitruv.framework.views.changederivation.StateBasedChangeResolutionStrategy;

@RequiredArgsConstructor
public class ModelStateDiff {
  private final StateBasedChangeResolutionStrategy strategy;

  public ModelStateDiff() {
    this(new DefaultStateBasedChangeResolutionStrategy());
  }

  private static Map<String, Resource> byFileName(Collection<Resource> resources) {
    Map<String, Resource> byName = new LinkedHashMap<>();
    for (Resource resource : resources) {
      String name =
          resource.getURI() != null ? resource.getURI().lastSegment() : resource.toString();
      byName.put(name, resource);
    }

    return byName;
  }

  public VitruviusChange<HierarchicalId> between(Resource newState, Resource oldState) {
    return strategy.getChangeSequenceBetween(newState, oldState);
  }

  public long changeCount(Collection<Resource> current, Collection<Resource> proposed) {
    Map<String, Resource> currentByName = byFileName(current);
    Map<String, Resource> proposedByName = byFileName(proposed);

    Set<String> names = new LinkedHashSet<>(currentByName.keySet());
    names.addAll(proposedByName.keySet());

    long total = 0;
    for (String name : names) {
      total += changeCountFor(currentByName.get(name), proposedByName.get(name));
    }

    return total;
  }

  private long changeCountFor(Resource current, Resource proposed) {
    if (current != null && proposed != null) {
      return strategy.getChangeSequenceBetween(proposed, current).getEChanges().size();
    }

    if (proposed == null) {
      return strategy.getChangeSequenceForDeleted(current).getEChanges().size();
    }

    return strategy.getChangeSequenceForCreated(proposed).getEChanges().size();
  }
}
