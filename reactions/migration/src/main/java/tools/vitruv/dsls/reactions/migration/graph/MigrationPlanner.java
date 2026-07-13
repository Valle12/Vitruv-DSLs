package tools.vitruv.dsls.reactions.migration.graph;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import tools.vitruv.dsls.reactions.migration.strategy.DominanceContext;
import tools.vitruv.dsls.reactions.migration.strategy.DominanceStrategy;

@RequiredArgsConstructor
public class MigrationPlanner {
  private final PropagationGraph graph;
  private final DominanceStrategy strategy;

  public DominancePlan plan(DominanceContext context) {
    List<MetamodelNode> sources = coveringSources(context);
    List<MetamodelNode> derived =
        context.presentNodes().stream().filter(node -> !sources.contains(node)).toList();
    return new DominancePlan(sources, derived);
  }

  public List<MetamodelNode> coveringSources(DominanceContext context) {
    Set<MetamodelNode> present = context.presentNodes();
    List<MetamodelNode> sources = new ArrayList<>();
    Set<MetamodelNode> covered = new LinkedHashSet<>();
    while (!covered.containsAll(present)) {
      Set<MetamodelNode> remaining = new LinkedHashSet<>(present);
      remaining.removeAll(covered);

      MetamodelNode next =
          strategy
              .selectDominant(new RestrictedContext(context, remaining))
              .filter(remaining::contains)
              .orElseGet(() -> remaining.iterator().next());

      sources.add(next);
      covered.add(next);
      for (MetamodelNode reached : graph.reachableFrom(next)) {
        if (present.contains(reached)) {
          covered.add(reached);
        }
      }
    }

    return sources;
  }
}
