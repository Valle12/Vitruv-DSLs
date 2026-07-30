package tools.vitruv.dsls.reactions.migration.migration;

import java.util.Optional;

public record SelectiveOutcome(
    MigrationMode mode,
    boolean attempted,
    boolean fellBackToFull,
    Optional<String> fallbackReason,
    int dirtyRuleCount,
    int affectedElementCount) {

  public static SelectiveOutcome notRequested() {
    return new SelectiveOutcome(MigrationMode.FULL, false, false, Optional.empty(), 0, 0);
  }

  public static SelectiveOutcome clean(MigrationMode mode) {
    return new SelectiveOutcome(mode, true, false, Optional.empty(), 0, 0);
  }

  public static SelectiveOutcome applied(
      MigrationMode mode, int dirtyRuleCount, int affectedElementCount) {
    return new SelectiveOutcome(
        mode, true, false, Optional.empty(), dirtyRuleCount, affectedElementCount);
  }

  public static SelectiveOutcome fellBack(MigrationMode mode, String reason) {
    return new SelectiveOutcome(mode, true, true, Optional.of(reason), 0, 0);
  }
}
