package tools.vitruv.migration;

import java.util.Set;

public record ReachableNodes(Set<Set<String>> reachableNodes) {
  public static final ReachableNodes EMPTY = new ReachableNodes(Set.of());

  public boolean contains(Set<String> node) {
    return reachableNodes.contains(node);
  }
}
