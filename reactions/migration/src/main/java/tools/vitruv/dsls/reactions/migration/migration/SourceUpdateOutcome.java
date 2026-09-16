package tools.vitruv.dsls.reactions.migration.migration;

import java.util.List;

/**
 * What the source update did after a full migration.
 *
 * @param attempted whether a source update was run at all
 * @param rounds how many rounds it took
 * @param converged whether a round finally left the dominant model unchanged
 * @param lastDeltaSize how many differences the last round still found
 * @param retractions a description of each difference that took content out of the dominant
 *     model, recorded so that the run can report what the update removed
 */
public record SourceUpdateOutcome(
    boolean attempted,
    int rounds,
    boolean converged,
    long lastDeltaSize,
    List<String> retractions) {
  /**
   * Returns the outcome of a run that updated no sources.
   *
   * @return an outcome recording that nothing was attempted
   */
  public static SourceUpdateOutcome skipped() {
    return new SourceUpdateOutcome(false, 0, false, 0, List.of());
  }
}
