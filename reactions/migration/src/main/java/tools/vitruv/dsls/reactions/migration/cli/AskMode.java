package tools.vitruv.dsls.reactions.migration.cli;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum AskMode {
  AUTO,
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
