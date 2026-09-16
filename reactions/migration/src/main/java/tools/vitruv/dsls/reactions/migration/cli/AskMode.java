package tools.vitruv.dsls.reactions.migration.cli;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Whether a migration may put an ambiguous placement to the caller. */
public enum AskMode {
  /** Asks whenever a console is available, and reports the case otherwise. */
  AUTO,
  /** Asks nothing and reports every ambiguous placement as an open decision. */
  NEVER;

  static final List<String> TOKENS = List.of("auto", "never");

  static Optional<AskMode> fromToken(String token) {
    return switch (token.toLowerCase(Locale.ROOT)) {
      case "auto" -> Optional.of(AUTO);
      case "never" -> Optional.of(NEVER);
      default -> Optional.empty();
    };
  }
}
