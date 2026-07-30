package tools.vitruv.dsls.reactions.migration.migration;

import java.time.Duration;
import java.util.List;

public record MigrationStatistics(Duration total, List<PhaseDuration> phases) {
  public List<String> phaseNames() {
    return phases.stream().map(PhaseDuration::phase).toList();
  }

  public record PhaseDuration(String phase, Duration duration) {}
}
