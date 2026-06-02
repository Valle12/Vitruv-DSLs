package tools.vitruv.migration;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class MaxReachabilityDominanceStrategy implements DominanceStrategy {
  private static int countReachablePresent(
      PropagationGraph graph, Set<String> node, Collection<Set<String>> presentNodes) {
    ReachableNodes reachable = graph.reachableFrom(node);
    return (int)
        presentNodes.stream()
            .filter(other -> !other.equals(node) && reachable.contains(other))
            .count();
  }

  @Override
  public Optional<Set<String>> selectDominant(DominanceContext context) {
    PropagationGraph graph = context.graph();
    Collection<Set<String>> presentNodes = context.presentNodes();
    List<Set<String>> candidates = List.copyOf(presentNodes);
    if (candidates.isEmpty()) {
      return Optional.empty();
    }

    Comparator<Set<String>> byOutDegreeThenReach =
        Comparator.comparingInt(graph::outDegree)
            .thenComparingInt(node -> countReachablePresent(graph, node, presentNodes));

    List<Set<String>> reachingAll =
        candidates.stream().filter(node -> graph.reachesAll(node, presentNodes)).toList();
    if (!reachingAll.isEmpty()) {
      return reachingAll.stream().max(byOutDegreeThenReach);
    }

    return candidates.stream()
        .max(
            Comparator.<Set<String>>comparingInt(
                    node -> countReachablePresent(graph, node, presentNodes))
                .thenComparing(byOutDegreeThenReach));
  }
}
