package tools.vitruv.dsls.reactions.migration;

import java.util.Set;
import java.util.function.ToLongFunction;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;
import tools.vitruv.dsls.reactions.migration.strategy.DominanceContext;

public final class TestDominanceContexts {
  private TestDominanceContexts() {}

  public static DominanceContext structuralOnly(
      PropagationGraph graph, Set<MetamodelNode> present) {
    return withTrialCounts(
        graph,
        present,
        candidate -> {
          throw new UnsupportedOperationException("structural strategy must not run trials");
        });
  }

  public static DominanceContext withTrialCounts(
      PropagationGraph graph,
      Set<MetamodelNode> present,
      ToLongFunction<MetamodelNode> trialCounts) {
    return new DominanceContext() {
      @Override
      public PropagationGraph graph() {
        return graph;
      }

      @Override
      public Set<MetamodelNode> presentNodes() {
        return present;
      }

      @Override
      public long trialChangeCount(MetamodelNode candidate) {
        return trialCounts.applyAsLong(candidate);
      }
    };
  }
}
