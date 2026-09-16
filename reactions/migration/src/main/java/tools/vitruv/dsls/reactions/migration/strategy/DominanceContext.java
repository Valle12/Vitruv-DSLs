package tools.vitruv.dsls.reactions.migration.strategy;

import java.util.Set;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;

/** What a dominance strategy may ask about the situation it is choosing in. */
public interface DominanceContext {
  /**
   * Returns the propagation graph of the rule set the migration is moving to.
   *
   * @return the propagation graph
   */
  PropagationGraph graph();

  /**
   * Returns the metamodels the V-SUM actually holds a model for.
   *
   * @return the present metamodels
   */
  Set<MetamodelNode> presentNodes();

  /**
   * Runs a trial migration with the given candidate as dominant model and counts the changes it
   * proposes. Expensive, because the trial builds a V-SUM of its own.
   *
   * @param candidate the metamodel to try as the dominant one
   * @return the number of changes the trial proposed
   */
  long trialChangeCount(MetamodelNode candidate);
}
