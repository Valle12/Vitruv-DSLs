package tools.vitruv.strategy;

import java.util.Set;
import tools.vitruv.migration.PropagationGraph;

public interface DominanceContext {
  PropagationGraph graph();

  Set<Set<String>> presentNodes();

  long reDerivationLoss(Set<String> dominantNode);
}
