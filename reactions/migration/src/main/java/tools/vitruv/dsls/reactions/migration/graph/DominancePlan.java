package tools.vitruv.dsls.reactions.migration.graph;

import java.util.List;

/**
 * Which metamodels a full migration derives from and which it rebuilds.
 *
 * @param sources the metamodels kept as they are, the first of which is the dominant model
 * @param derived the metamodels rebuilt from the sources
 */
public record DominancePlan(List<MetamodelNode> sources, List<MetamodelNode> derived) {
  /**
   * Returns the dominant model of this plan.
   *
   * @return the first source, which every other model is derived from
   */
  public MetamodelNode dominant() {
    return sources.getFirst();
  }
}
