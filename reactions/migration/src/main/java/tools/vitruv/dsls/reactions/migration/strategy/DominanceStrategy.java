package tools.vitruv.dsls.reactions.migration.strategy;

import java.util.Optional;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;

public interface DominanceStrategy {
  Optional<MetamodelNode> selectDominant(DominanceContext context);
}
