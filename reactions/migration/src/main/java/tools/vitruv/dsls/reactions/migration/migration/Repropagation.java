package tools.vitruv.dsls.reactions.migration.migration;

import java.nio.file.Path;

record Repropagation(int affectedCount, Path preMigrationFolder) {
  static Repropagation none() {
    return new Repropagation(0, null);
  }

  boolean happened() {
    return affectedCount > 0;
  }
}
