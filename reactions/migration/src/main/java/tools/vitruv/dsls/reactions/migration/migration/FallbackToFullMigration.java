package tools.vitruv.dsls.reactions.migration.migration;

/** Thrown inside the selective path to hand the run over to a full migration. */
class FallbackToFullMigration extends RuntimeException {
  FallbackToFullMigration(String reason) {
    super(reason);
  }

  FallbackToFullMigration(String reason, Throwable cause) {
    super(reason, cause);
  }
}
