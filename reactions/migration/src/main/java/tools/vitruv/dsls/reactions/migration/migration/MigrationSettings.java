package tools.vitruv.dsls.reactions.migration.migration;

public record MigrationSettings(boolean updateSources, int maxSourceUpdateRounds) {
  public static MigrationSettings defaults() {
    return new MigrationSettings(true, 3);
  }

  public static MigrationSettings forwardOnly() {
    return new MigrationSettings(false, 0);
  }
}
