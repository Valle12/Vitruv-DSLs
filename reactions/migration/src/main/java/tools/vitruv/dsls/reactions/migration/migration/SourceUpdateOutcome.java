package tools.vitruv.dsls.reactions.migration.migration;

import java.util.List;

public record SourceUpdateOutcome(
    boolean attempted,
    int rounds,
    boolean converged,
    long lastDeltaSize,
    List<String> retractions) {
  public static SourceUpdateOutcome skipped() {
    return new SourceUpdateOutcome(false, 0, false, 0, List.of());
  }
}
