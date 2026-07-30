package tools.vitruv.dsls.reactions.migration.migration;

import tools.vitruv.dsls.reactions.migration.preservation.PreservationPolicy;

public record MigrationSettings(
    boolean updateSources,
    int maxSourceUpdateRounds,
    MigrationMode mode,
    PreservationPolicy preservation,
    boolean askOnAmbiguity) {
  public static MigrationSettings defaults() {
    return new MigrationSettings(true, 3, MigrationMode.FULL, PreservationPolicy.USER, false);
  }

  public static MigrationSettings forwardOnly() {
    return new MigrationSettings(false, 0, MigrationMode.FULL, PreservationPolicy.USER, false);
  }

  public MigrationSettings withMode(MigrationMode newMode) {
    return new MigrationSettings(
        updateSources, maxSourceUpdateRounds, newMode, preservation, askOnAmbiguity);
  }

  public MigrationSettings withPreservation(PreservationPolicy policy) {
    return new MigrationSettings(
        updateSources, maxSourceUpdateRounds, mode, policy, askOnAmbiguity);
  }

  public MigrationSettings withAsking(boolean asking) {
    return new MigrationSettings(updateSources, maxSourceUpdateRounds, mode, preservation, asking);
  }
}
