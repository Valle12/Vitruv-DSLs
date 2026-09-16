package tools.vitruv.dsls.reactions.migration.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import tools.vitruv.change.propagation.ChangePropagationSpecification;

/**
 * Which metamodel a rule set propagates into which other, with one node per metamodel and one
 * edge per change propagation specification.
 */
public class PropagationGraph {
  private final Map<MetamodelNode, Set<MetamodelNode>> outgoing = new HashMap<>();

  /**
   * Builds the graph of the given rule set.
   *
   * @param specifications the change propagation specifications the rule set compiles to
   */
  public PropagationGraph(List<ChangePropagationSpecification> specifications) {
    for (ChangePropagationSpecification specification : specifications) {
      MetamodelNode source = MetamodelNode.of(specification.getSourceMetamodelDescriptor());
      MetamodelNode target = MetamodelNode.of(specification.getTargetMetamodelDescriptor());
      outgoing.computeIfAbsent(source, key -> new LinkedHashSet<>()).add(target);
      outgoing.computeIfAbsent(target, key -> new LinkedHashSet<>());
    }
  }

  /**
   * Returns every metamodel the rule set mentions, whether it propagates from it or into it.
   *
   * @return the nodes of the graph
   */
  public Set<MetamodelNode> nodes() {
    return Set.copyOf(outgoing.keySet());
  }

  /**
   * Returns into how many metamodels the given one propagates directly.
   *
   * @param node the metamodel to count for
   * @return the number of outgoing edges
   */
  public int outDegree(MetamodelNode node) {
    return outgoing.getOrDefault(node, Set.of()).size();
  }

  /**
   * Returns the metamodels reachable from the given one along any number of propagations.
   *
   * @param start the metamodel to start at
   * @return the reachable metamodels, which contain the start itself only when it lies on a cycle
   */
  public Set<MetamodelNode> reachableFrom(MetamodelNode start) {
    Set<MetamodelNode> reachable = new LinkedHashSet<>();
    Deque<MetamodelNode> queue = new ArrayDeque<>(outgoing.getOrDefault(start, Set.of()));
    while (!queue.isEmpty()) {
      MetamodelNode current = queue.poll();
      if (reachable.add(current)) {
        queue.addAll(outgoing.getOrDefault(current, Set.of()));
      }
    }

    return reachable;
  }

  /**
   * Returns whether each of the given metamodels either is the start or is reachable from it.
   *
   * @param start the metamodel to start at
   * @param targets the metamodels that have to be reached
   * @return whether every one of them is reached
   */
  public boolean reachesAll(MetamodelNode start, Set<MetamodelNode> targets) {
    Set<MetamodelNode> reachable = reachableFrom(start);
    return targets.stream()
        .allMatch(target -> target.equals(start) || reachable.contains(target));
  }

  /**
   * Returns the node that owns the given namespace URI.
   *
   * @param nsUri the namespace URI to look up
   * @return the owning node, empty when no node of the graph owns it
   */
  public Optional<MetamodelNode> nodeContaining(String nsUri) {
    return outgoing.keySet().stream().filter(node -> node.owns(nsUri)).findFirst();
  }

  @Override
  public String toString() {
    List<MetamodelNode> sortedNodes = new ArrayList<>(outgoing.keySet());
    sortedNodes.sort(Comparator.comparing(node -> String.join(",", node.nsUris())));
    StringBuilder builder = new StringBuilder("propagation graph:");
    for (MetamodelNode node : sortedNodes) {
      String targets =
          outgoing.get(node).stream()
              .map(MetamodelNode::shortName)
              .collect(Collectors.joining(", "));
      builder.append("\n  ").append(node.shortName()).append(" -> [").append(targets).append("]");
    }

    return builder.toString();
  }
}
