package tools.vitruv.dsls.reactions.migration.cli;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum SourceUpdateMode {
  NONE,
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
