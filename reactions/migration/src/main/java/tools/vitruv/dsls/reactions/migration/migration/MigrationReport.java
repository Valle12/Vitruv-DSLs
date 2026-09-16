package tools.vitruv.dsls.reactions.migration.migration;

import java.util.List;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;
import tools.vitruv.dsls.reactions.migration.preservation.PreservationOutcome;

/**
 * What one migration run did.
 *
 * @param migrated whether the run changed the V-SUM at all
 * @param sources the metamodels it derived from
 * @param derived the metamodels it rebuilt
 * @param selection how the dominant model was chosen and what the trials cost
 * @param requestedInteractions the interactions the reactions asked for while it ran
 * @param sourceUpdate what the source update carried back into the dominant model
 * @param selective what the selective path found and did
 * @param preservation what content it carried over and what it could not
 * @param statistics how long the run took, in total and per phase
 */
public record MigrationReport(
    boolean migrated,
    List<MetamodelNode> sources,
    List<MetamodelNode> derived,
    SourceSelection selection,
    List<String> requestedInteractions,
    SourceUpdateOutcome sourceUpdate,
    SelectiveOutcome selective,
    PreservationOutcome preservation,
    MigrationStatistics statistics) {
  /**
   * Returns the report of a run that found nothing to do.
   *
   * @param selective what the rule comparison found
   * @param statistics the timings of the run
   * @return a report recording that the V-SUM was left as it was
   */
  public static MigrationReport nothingToDo(
      SelectiveOutcome selective, MigrationStatistics statistics) {
    return new MigrationReport(
        false,
        List.of(),
        List.of(),
        SourceSelection.none(),
        List.of(),
        SourceUpdateOutcome.skipped(),
        selective,
        PreservationOutcome.skipped(),
        statistics);
  }
}
