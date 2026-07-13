package tools.vitruv.dsls.reactions.migration.migration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
  private final Map<String, List<EObject>> rootsByNsUri;
  private final AdapterRegistry adapters;
  private final TrialMigration trialMigration;

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
    return trialMigration.changeCountFor(
        candidate, rootsByNsUri, fileResourceUrisOwnedBy(candidate));
  }

  private List<URI> fileResourceUrisOwnedBy(MetamodelNode node) {
    Set<URI> uris = new LinkedHashSet<>();
    try (View view = Vsums.openViewOfAll(sourceVsum, "resources-of-" + node.shortName())) {
      for (EObject root : view.getRootObjects()) {
        Resource resource = root.eResource();
        if (resource != null
            && resource.getURI() != null
            && resource.getURI().isFile()
            && !adapters.isPlatformLibraryResource(resource.getURI())
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
