package tools.vitruv.dsls.reactions.migration.migration;

import java.util.List;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;

public record MigrationReport(
    boolean migrated,
    List<MetamodelNode> sources,
    List<MetamodelNode> derived,
    List<String> requestedInteractions,
    SourceUpdateOutcome sourceUpdate) {
  public static MigrationReport nothingToDo() {
    return new MigrationReport(
        false, List.of(), List.of(), List.of(), SourceUpdateOutcome.skipped());
  }
}
