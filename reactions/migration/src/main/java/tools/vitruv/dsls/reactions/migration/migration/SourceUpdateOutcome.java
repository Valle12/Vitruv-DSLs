package tools.vitruv.dsls.reactions.migration.migration;

public record SourceUpdateOutcome(
    boolean attempted, int rounds, boolean converged, long lastDeltaSize) {
  public static SourceUpdateOutcome skipped() {
    return new SourceUpdateOutcome(false, 0, false, 0);
  }
}
