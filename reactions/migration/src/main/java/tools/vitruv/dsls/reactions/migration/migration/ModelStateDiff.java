package tools.vitruv.dsls.reactions.migration.migration;

import static com.google.common.base.Preconditions.checkArgument;

import edu.kit.ipd.sdq.commons.util.org.eclipse.emf.ecore.resource.ResourceCopier;
import edu.kit.ipd.sdq.commons.util.org.eclipse.emf.ecore.resource.ResourceUtil;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.common.util.BasicMonitor;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.EMFCompare;
import org.eclipse.emf.compare.match.IMatchEngine;
import org.eclipse.emf.compare.match.impl.MatchEngineFactoryImpl;
import org.eclipse.emf.compare.match.impl.MatchEngineFactoryRegistryImpl;
import org.eclipse.emf.compare.merge.BatchMerger;
import org.eclipse.emf.compare.merge.IMerger;
import org.eclipse.emf.compare.scope.DefaultComparisonScope;
import org.eclipse.emf.compare.utils.UseIdentifiers;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import tools.vitruv.change.atomic.EChange;
import tools.vitruv.change.composite.recording.ChangeRecorder;

@Slf4j
public final class ModelStateDiff {
  private static final UseIdentifiers MATCHING = UseIdentifiers.NEVER;

  static Map<String, Resource> byFileName(Collection<Resource> resources) {
    Map<String, Resource> byName = new LinkedHashMap<>();
    for (Resource resource : resources) {
      String name =
          resource.getURI() != null ? resource.getURI().lastSegment() : resource.toString();
      byName.put(name, resource);
    }

    return byName;
  }

  private static List<EChange<EObject>> recordChanges(Resource resource, Runnable function) {
    try (ChangeRecorder recorder = new ChangeRecorder(resource.getResourceSet())) {
      recorder.beginRecording();
      recorder.addToRecording(resource);
      function.run();
      return recorder.endRecording().getEChanges();
    }
  }

  private static void compareAndMerge(Notifier newState, Notifier currentState) {
    DefaultComparisonScope scope = new DefaultComparisonScope(newState, currentState, null);
    IMatchEngine.Factory.Registry matchEngines =
        MatchEngineFactoryRegistryImpl.createStandaloneInstance();
    matchEngines.add(new MatchEngineFactoryImpl(MATCHING));
    EMFCompare emfCompare =
        EMFCompare.builder().setMatchEngineFactoryRegistry(matchEngines).build();
    Comparison comparison = emfCompare.compare(scope);

    BatchMerger merger = new BatchMerger(IMerger.RegistryImpl.createStandaloneInstance());
    merger.copyAllLeftToRight(comparison.getDifferences(), new BasicMonitor());
  }

  private static void checkNoProxies(Resource resource, String stateNotice) {
    List<String> proxies =
        StreamSupport.stream(ResourceUtil.getReferencedProxies(resource).spliterator(), false)
            .map(Object::toString)
            .toList();
    checkArgument(
        proxies.isEmpty(),
        "%s '%s' should not contain proxies, but contains the following: %s",
        stateNotice,
        resource.getURI(),
        String.join(", ", proxies));
  }

  public long changeCount(Collection<Resource> current, Collection<Resource> proposed) {
    Map<String, Resource> currentByName = byFileName(current);
    Map<String, Resource> proposedByName = byFileName(proposed);

    Set<String> names = new LinkedHashSet<>(currentByName.keySet());
    names.addAll(proposedByName.keySet());

    long total = 0;
    for (String name : names) {
      total += changeCountFor(name, currentByName.get(name), proposedByName.get(name));
    }

    return total;
  }

  public List<EChange<EObject>> changesBetween(Resource newState, Resource oldState) {
    checkArgument(oldState != null && newState != null, "old state or new state must not be null");
    checkNoProxies(newState, "new state");
    checkNoProxies(oldState, "old state");
    Resource copy = ResourceCopier.copyViewResource(oldState, new ResourceSetImpl());
    return recordChanges(
        copy,
        () -> {
          if (!oldState.getURI().equals(newState.getURI())) {
            copy.setURI(newState.getURI());
          }
          compareAndMerge(newState, copy);
        });
  }

  public List<EChange<EObject>> changesForCreated(Resource newState) {
    checkArgument(newState != null, "new state must not be null");
    checkNoProxies(newState, "new state");
    ResourceSet monitored = new ResourceSetImpl();
    Resource created = monitored.createResource(newState.getURI());
    created.getContents().clear();
    return recordChanges(
        created, () -> created.getContents().addAll(EcoreUtil.copyAll(newState.getContents())));
  }

  public List<EChange<EObject>> changesForDeleted(Resource oldState) {
    checkArgument(oldState != null, "old state must not be null");
    checkNoProxies(oldState, "old state");
    Resource copy = ResourceCopier.copyViewResource(oldState, new ResourceSetImpl());
    return recordChanges(copy, () -> copy.getContents().clear());
  }

  private long changeCountFor(String name, Resource current, Resource proposed) {
    try {
      List<EChange<EObject>> changes;
      if (current != null && proposed != null) {
        changes = changesBetween(proposed, current);
      } else if (proposed == null) {
        changes = changesForDeleted(current);
      } else {
        changes = changesForCreated(proposed);
      }

      log.debug("{} change(s) in {}", changes.size(), name);
      return changes.size();
    } catch (RuntimeException e) {
      throw new IllegalStateException(
          "Counting the changes of " + name + " failed: " + e.getMessage(), e);
    }
  }
}
