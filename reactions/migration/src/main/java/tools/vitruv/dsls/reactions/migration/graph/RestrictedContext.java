package tools.vitruv.dsls.reactions.migration.graph;

import java.util.Set;
import tools.vitruv.dsls.reactions.migration.strategy.DominanceContext;

/**
 * A dominance context that hides all but a subset of the present metamodels, so that a strategy
 * can be asked to choose again among the ones an earlier choice left out of reach.
 */
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
