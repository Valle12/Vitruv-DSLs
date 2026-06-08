package tools.vitruv.strategy;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import tools.vitruv.migration.PropagationGraph;
import tools.vitruv.migration.ReachableNodes;

public class MaxReachabilityDominanceStrategy implements DominanceStrategy {
  private long countReachablePresent(
      PropagationGraph graph, Set<String> node, Collection<Set<String>> presentNodes) {
    ReachableNodes reachable = graph.reachableFrom(node);
    return presentNodes.stream()
        .filter(other -> !other.equals(node) && reachable.contains(other))
        .count();
  }

  @Override
  public Optional<Set<String>> selectDominant(DominanceContext context) {
    PropagationGraph graph = context.graph();
    Set<Set<String>> presentNodes = context.presentNodes();
    if (presentNodes.isEmpty()) {
      return Optional.empty();
    }

    Comparator<Set<String>> byOutDegreeThenReach =
        Comparator.comparingLong(graph::outDegree)
            .thenComparingLong(node -> countReachablePresent(graph, node, presentNodes));

    List<Set<String>> reachingAll =
        presentNodes.stream().filter(node -> graph.reachesAll(node, presentNodes)).toList();
    if (!reachingAll.isEmpty()) {
      return reachingAll.stream().max(byOutDegreeThenReach);
    }

    return presentNodes.stream()
        .max(
            Comparator.comparingLong(
                    (Set<String> node) -> countReachablePresent(graph, node, presentNodes))
                .thenComparing(byOutDegreeThenReach));
  }
}
