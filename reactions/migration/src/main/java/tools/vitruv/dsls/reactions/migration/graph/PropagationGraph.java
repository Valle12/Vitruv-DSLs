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

public class PropagationGraph {
  private final Map<MetamodelNode, Set<MetamodelNode>> outgoing = new HashMap<>();

  public PropagationGraph(List<ChangePropagationSpecification> specifications) {
    for (ChangePropagationSpecification specification : specifications) {
      MetamodelNode source = MetamodelNode.of(specification.getSourceMetamodelDescriptor());
      MetamodelNode target = MetamodelNode.of(specification.getTargetMetamodelDescriptor());
      outgoing.computeIfAbsent(source, key -> new LinkedHashSet<>()).add(target);
      outgoing.computeIfAbsent(target, key -> new LinkedHashSet<>());
    }
  }

  public Set<MetamodelNode> nodes() {
    return Set.copyOf(outgoing.keySet());
  }

  public int outDegree(MetamodelNode node) {
    return outgoing.getOrDefault(node, Set.of()).size();
  }

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

  public boolean reachesAll(MetamodelNode start, Set<MetamodelNode> targets) {
    Set<MetamodelNode> reachable = reachableFrom(start);
    return targets.stream()
        .allMatch(target -> target.equals(start) || reachable.contains(target));
  }

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
