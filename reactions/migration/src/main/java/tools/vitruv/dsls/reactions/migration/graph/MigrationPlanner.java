package tools.vitruv.dsls.reactions.migration.graph;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import tools.vitruv.dsls.reactions.migration.strategy.DominanceContext;
import tools.vitruv.dsls.reactions.migration.strategy.DominanceStrategy;

/**
 * Turns the propagation graph and a dominance strategy into the plan of a full migration, by
 * naming the metamodels to derive from and the metamodels to rebuild.
 */
@RequiredArgsConstructor
public class MigrationPlanner {
  private final PropagationGraph graph;
  private final DominanceStrategy strategy;

  /**
   * Plans a full migration for the given situation.
   *
   * @param context what the strategy may ask about the situation
   * @return the metamodels to derive from and the metamodels to rebuild
   */
  public DominancePlan plan(DominanceContext context) {
    List<MetamodelNode> sources = coveringSources(context);
    List<MetamodelNode> derived =
        context.presentNodes().stream().filter(node -> !sources.contains(node)).toList();
    return new DominancePlan(sources, derived);
  }

  /**
   * Chooses as few metamodels as possible that together reach every present one. The strategy
   * names the first, and for as long as metamodels remain out of reach it is asked again among
   * those left over, so that a rule set whose graph falls apart still gets a complete plan.
   *
   * @param context what the strategy may ask about the situation
   * @return the chosen metamodels, the first of which is the dominant model
   */
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
