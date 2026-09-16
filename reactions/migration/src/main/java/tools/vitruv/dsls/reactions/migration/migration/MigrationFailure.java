package tools.vitruv.dsls.reactions.migration.migration;

import java.util.List;
import tools.vitruv.dsls.reactions.migration.graph.DominancePlan;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;

/**
 * Thrown when re-deriving the derived models fails. It carries the plan the run was following,
 * so that a report can still name the metamodels involved and how the dominant one was chosen.
 */
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

  /**
   * Returns the metamodels the failed run was deriving from.
   *
   * @return the sources of its plan
   */
  public List<MetamodelNode> sources() {
    return sources;
  }

  /**
   * Returns the metamodels the failed run was rebuilding.
   *
   * @return the derived metamodels of its plan
   */
  public List<MetamodelNode> derived() {
    return derived;
  }

  /**
   * Returns how the dominant model of the failed run was chosen.
   *
   * @return its source selection
   */
  public SourceSelection selection() {
    return selection;
  }
}
