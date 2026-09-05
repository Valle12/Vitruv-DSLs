package tools.vitruv.dsls.reactions.migration.migration;

import java.util.List;
import tools.vitruv.dsls.reactions.migration.graph.DominancePlan;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;

public final class MigrationFailure extends RuntimeException {
  private final transient List<MetamodelNode> sources;
  private final transient List<MetamodelNode> derived;
  private final transient SourceSelection selection;

  MigrationFailure(DominancePlan plan, SourceSelection selection, RuntimeException cause) {
    super(
        "Re-deriving "
            + shortNames(plan.derived())
            + " from "
            + shortNames(plan.sources())
            + " failed: "
            + cause.getMessage(),
        cause);
    this.sources = List.copyOf(plan.sources());
    this.derived = List.copyOf(plan.derived());
    this.selection = selection;
  }

  private static List<String> shortNames(List<MetamodelNode> nodes) {
    return nodes.stream().map(MetamodelNode::shortName).toList();
  }

  public List<MetamodelNode> sources() {
    return sources;
  }

  public List<MetamodelNode> derived() {
    return derived;
  }

  public SourceSelection selection() {
    return selection;
  }
}
