package tools.vitruv.dsls.reactions.migration.migration;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;
import tools.vitruv.dsls.reactions.migration.strategy.DominanceContext;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

@RequiredArgsConstructor
final class RunnerContext implements DominanceContext {
  private final PropagationGraph graph;
  private final InternalVirtualModel sourceVsum;
  private final Set<MetamodelNode> presentNodes;
  private final AdapterRegistry adapters;
  private final TrialMigration trialMigration;
  private final Path vsumFolder;
  private final List<SourceSelection.Trial> trials = new ArrayList<>();
  private final Map<MetamodelNode, Path> trialFolders = new LinkedHashMap<>();

  private static Duration elapsedSince(long nanoTimestamp) {
    return Duration.ofNanos(System.nanoTime() - nanoTimestamp);
  }

  @Override
  public PropagationGraph graph() {
    return graph;
  }

  @Override
  public Set<MetamodelNode> presentNodes() {
    return presentNodes;
  }

  @Override
  public long trialChangeCount(MetamodelNode candidate) {
    long startedAt = System.nanoTime();
    try {
      TrialMigration.Outcome outcome =
          trialMigration.run(candidate, fileResourceUrisOwnedBy(candidate));
      trials.add(
          SourceSelection.Trial.completed(
              candidate, outcome.changeCount(), elapsedSince(startedAt)));
      trialFolders.put(candidate, outcome.folder());
      return outcome.changeCount();
    } catch (RuntimeException e) {
      trials.add(SourceSelection.Trial.failed(candidate, elapsedSince(startedAt), e));
      throw e;
    }
  }

  SourceSelection selection() {
    return SourceSelection.of(List.copyOf(presentNodes), trials);
  }

  Optional<Path> trialFolderOf(MetamodelNode candidate) {
    return Optional.ofNullable(trialFolders.get(candidate));
  }

  private List<URI> fileResourceUrisOwnedBy(MetamodelNode node) {
    Set<URI> uris = new LinkedHashSet<>();
    try (View view = Vsums.openViewOfAll(sourceVsum, "resources-of-" + node.shortName())) {
      for (EObject root : view.getRootObjects()) {
        Resource resource = root.eResource();
        if (resource != null
            && resource.getURI() != null
            && resource.getURI().isFile()
            && !adapters.isPlatformLibraryResource(resource.getURI(), vsumFolder)
            && node.owns(root.eClass().getEPackage().getNsURI())) {
          uris.add(resource.getURI());
        }
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to collect resources of " + node.shortName(), e);
    }

    return List.copyOf(uris);
  }
}
