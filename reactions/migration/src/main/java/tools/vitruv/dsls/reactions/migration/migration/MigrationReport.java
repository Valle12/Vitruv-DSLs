package tools.vitruv.dsls.reactions.migration.migration;

import java.util.List;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;
import tools.vitruv.dsls.reactions.migration.preservation.PreservationOutcome;

public record MigrationReport(
    boolean migrated,
    List<MetamodelNode> sources,
    List<MetamodelNode> derived,
    List<String> requestedInteractions,
    SourceUpdateOutcome sourceUpdate,
    SelectiveOutcome selective,
    PreservationOutcome preservation,
    MigrationStatistics statistics) {
  public static MigrationReport nothingToDo(
      SelectiveOutcome selective, MigrationStatistics statistics) {
    return new MigrationReport(
        false,
        List.of(),
        List.of(),
        List.of(),
        SourceUpdateOutcome.skipped(),
        selective,
        PreservationOutcome.skipped(),
        statistics);
  }
}
