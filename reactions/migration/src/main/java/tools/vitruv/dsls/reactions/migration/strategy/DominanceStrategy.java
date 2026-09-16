package tools.vitruv.dsls.reactions.migration.strategy;

import java.util.Optional;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;

/** Chooses the dominant model that a full migration derives the other models from. */
public interface DominanceStrategy {
  /**
   * Chooses the dominant model for the given situation.
   *
   * @param context what may be asked about the situation
   * @return the chosen metamodel, empty when this strategy cannot decide
   */
  Optional<MetamodelNode> selectDominant(DominanceContext context);
}
