package tools.vitruv.dsls.reactions.migration.graph;

import java.util.Set;
import tools.vitruv.dsls.reactions.migration.strategy.DominanceContext;

record RestrictedContext(DominanceContext base, Set<MetamodelNode> presentNodes)
    implements DominanceContext {
  @Override
  public PropagationGraph graph() {
    return base.graph();
  }

  @Override
  public long trialChangeCount(MetamodelNode candidate) {
    return base.trialChangeCount(candidate);
  }
}
