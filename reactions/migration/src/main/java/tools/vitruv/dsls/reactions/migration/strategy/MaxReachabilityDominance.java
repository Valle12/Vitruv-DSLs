package tools.vitruv.dsls.reactions.migration.strategy;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;

public class MaxReachabilityDominance implements DominanceStrategy {
  private static long countReachablePresent(
      PropagationGraph graph, MetamodelNode node, Set<MetamodelNode> presentNodes) {
    Set<MetamodelNode> reachable = graph.reachableFrom(node);
    return presentNodes.stream()
        .filter(other -> !other.equals(node) && reachable.contains(other))
        .count();
  }

  @Override
  public Optional<MetamodelNode> selectDominant(DominanceContext context) {
    PropagationGraph graph = context.graph();
    Set<MetamodelNode> presentNodes = context.presentNodes();
    if (presentNodes.isEmpty()) {
      return Optional.empty();
    }

    Comparator<MetamodelNode> byOutDegreeThenReach =
        Comparator.comparingLong(graph::outDegree)
            .thenComparingLong(node -> countReachablePresent(graph, node, presentNodes));

    List<MetamodelNode> reachingAll =
        presentNodes.stream().filter(node -> graph.reachesAll(node, presentNodes)).toList();
    if (!reachingAll.isEmpty()) {
      return reachingAll.stream().max(byOutDegreeThenReach);
    }

    return presentNodes.stream()
        .max(
            Comparator.comparingLong(
                    (MetamodelNode node) -> countReachablePresent(graph, node, presentNodes))
                .thenComparing(byOutDegreeThenReach));
  }
}
