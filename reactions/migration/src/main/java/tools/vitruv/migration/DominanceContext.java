package tools.vitruv.migration;

import java.util.Collection;
import java.util.Set;

public interface DominanceContext {
  PropagationGraph graph();

  Collection<Set<String>> presentNodes();

  /**
   * Re-derive all models from the given dominant candidate in an isolated temp VSUM, evaluate with
   * reaction set and count atomic changes. Lower is better
   */
  long reDerivationLoss(Set<String> dominantNode);
}
