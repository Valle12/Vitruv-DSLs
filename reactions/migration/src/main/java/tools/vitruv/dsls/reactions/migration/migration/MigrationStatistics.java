package tools.vitruv.dsls.reactions.migration.migration;

import java.time.Duration;
import java.util.List;

/**
 * How long a migration took, in total and per phase.
 *
 * @param total the time the whole run took
 * @param phases one entry per phase, in the order the phases ran
 */
public record MigrationStatistics(Duration total, List<PhaseDuration> phases) {
  /**
   * Returns the phases that ran, in order.
   *
   * @return the name of each phase
   */
  public List<String> phaseNames() {
    return phases.stream().map(PhaseDuration::phase).toList();
  }

  /**
   * How long one phase of a migration took.
   *
   * @param phase the name of the phase
   * @param duration how long it ran
   */
  public record PhaseDuration(String phase, Duration duration) {}
}
