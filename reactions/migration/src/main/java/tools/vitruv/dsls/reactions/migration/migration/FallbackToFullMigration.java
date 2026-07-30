package tools.vitruv.dsls.reactions.migration.migration;

class FallbackToFullMigration extends RuntimeException {
  FallbackToFullMigration(String reason) {
    super(reason);
  }

  FallbackToFullMigration(String reason, Throwable cause) {
    super(reason, cause);
  }
}
