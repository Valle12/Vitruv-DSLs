package tools.vitruv.dsls.reactions.migration.strategy;

import java.util.Set;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;

public interface DominanceContext {
  PropagationGraph graph();

  Set<MetamodelNode> presentNodes();

  long trialChangeCount(MetamodelNode candidate);
}
