package tools.vitruv.dsls.reactions.migration.migration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import tools.vitruv.dsls.reactions.migration.migration.MigrationStatistics.PhaseDuration;

class PhaseTimer {
  private final List<PhaseDuration> phases = new ArrayList<>();
  private final long startedAt = System.nanoTime();

  private static Duration elapsedSince(long nanoTimestamp) {
    return Duration.ofNanos(System.nanoTime() - nanoTimestamp);
  }

  <T> T time(String phase, Supplier<T> work) {
    long phaseStartedAt = System.nanoTime();
    try {
      return work.get();
    } finally {
      phases.add(new PhaseDuration(phase, elapsedSince(phaseStartedAt)));
    }
  }

  void time(String phase, Runnable work) {
    time(
        phase,
        () -> {
          work.run();
          return null;
        });
  }

  MigrationStatistics statistics() {
    return new MigrationStatistics(elapsedSince(startedAt), List.copyOf(phases));
  }
}
