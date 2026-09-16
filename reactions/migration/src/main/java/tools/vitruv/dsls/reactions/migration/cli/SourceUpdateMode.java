package tools.vitruv.dsls.reactions.migration.cli;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Whether a full migration carries information back into the dominant model. */
public enum SourceUpdateMode {
  /** Leaves the dominant model as it is, so that what only the derived models held is lost. */
  NONE,
  /**
   * Propagates the old derived models back into the dominant one and repeats until nothing
   * changes any more or the round limit is reached.
   */
  FIXPOINT;

  static final List<String> TOKENS = List.of("none", "fixpoint");

  static Optional<SourceUpdateMode> fromToken(String token) {
    return switch (token.toLowerCase(Locale.ROOT)) {
      case "none" -> Optional.of(NONE);
      case "fixpoint" -> Optional.of(FIXPOINT);
      default -> Optional.empty();
    };
  }
}
