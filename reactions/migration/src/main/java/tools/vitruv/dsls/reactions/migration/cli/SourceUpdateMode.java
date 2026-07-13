package tools.vitruv.dsls.reactions.migration.cli;

import java.util.Locale;
import java.util.Optional;

public enum SourceUpdateMode {
  NONE,
  FIXPOINT;

  static Optional<SourceUpdateMode> fromToken(String token) {
    return switch (token.toLowerCase(Locale.ROOT)) {
      case "none" -> Optional.of(NONE);
      case "fixpoint" -> Optional.of(FIXPOINT);
      default -> Optional.empty();
    };
  }
}
