package tools.vitruv.dsls.reactions.migration.graph;

import java.util.List;

public record DominancePlan(List<MetamodelNode> sources, List<MetamodelNode> derived) {
  public MetamodelNode dominant() {
    return sources.getFirst();
  }
}
