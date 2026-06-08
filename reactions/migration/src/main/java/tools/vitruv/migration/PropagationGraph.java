package tools.vitruv.migration;

import java.util.*;
import java.util.stream.Collectors;
import tools.vitruv.change.composite.MetamodelDescriptor;
import tools.vitruv.change.propagation.ChangePropagationSpecification;

public class PropagationGraph {
  private final Map<Set<String>, ReachableNodes> outgoing;

  public PropagationGraph(List<ChangePropagationSpecification> specifications) {
    outgoing = new HashMap<>();
    for (ChangePropagationSpecification specification : specifications) {
      Set<String> source = nodeOf(specification.getSourceMetamodelDescriptor());
      Set<String> target = nodeOf(specification.getTargetMetamodelDescriptor());
      outgoing.computeIfAbsent(source, key -> new ReachableNodes(new LinkedHashSet<>()));
      outgoing.computeIfAbsent(target, key -> new ReachableNodes(new LinkedHashSet<>()));
      outgoing.get(source).reachableNodes().add(target);
    }
  }

  private static Set<String> nodeOf(MetamodelDescriptor descriptor) {
    return new TreeSet<>(descriptor.getNsUris());
  }

  public static boolean nodeOwns(Set<String> node, String nsUri) {
    return node.stream()
        .anyMatch(declaredUri -> nsUri.equals(declaredUri) || nsUri.startsWith(declaredUri + "/"));
  }

  public static String shortName(Set<String> node) {
    return node.isEmpty() ? "<none>" : node.iterator().next();
  }

  public int outDegree(Set<String> node) {
    return outgoing.getOrDefault(node, ReachableNodes.EMPTY).reachableNodes().size();
  }

  public ReachableNodes reachableFrom(Set<String> start) {
    ReachableNodes reachable = new ReachableNodes(new HashSet<>());
    Deque<Set<String>> queue =
        new ArrayDeque<>(outgoing.getOrDefault(start, ReachableNodes.EMPTY).reachableNodes());
    while (!queue.isEmpty()) {
      Set<String> current = queue.poll();
      if (reachable.reachableNodes().add(current)) {
        queue.addAll(outgoing.getOrDefault(current, ReachableNodes.EMPTY).reachableNodes());
      }
    }

    return reachable;
  }

  public boolean reachesAll(Set<String> start, Set<Set<String>> targets) {
    ReachableNodes reachable = reachableFrom(start);
    for (Set<String> target : targets) {
      if (!target.equals(start) && !reachable.contains(target)) {
        return false;
      }
    }

    return true;
  }

  public Optional<Set<String>> nodeContaining(String nsUri) {
    return outgoing.keySet().stream().filter(node -> nodeOwns(node, nsUri)).findFirst();
  }

  @Override
  public String toString() {
    List<Set<String>> sortedNodes = new ArrayList<>(outgoing.keySet());
    sortedNodes.sort(Comparator.comparing(a -> String.join(",", a)));
    StringBuilder builder = new StringBuilder("propagation graph:");
    for (Set<String> node : sortedNodes) {
      String targets =
          outgoing.get(node).reachableNodes().stream()
              .map(PropagationGraph::shortName)
              .collect(Collectors.joining(", "));
      builder.append("\n  ").append(shortName(node)).append(" -> [").append(targets).append("]");
    }

    return builder.toString();
  }
}
