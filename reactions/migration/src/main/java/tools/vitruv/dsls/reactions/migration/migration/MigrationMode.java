package tools.vitruv.dsls.reactions.migration.migration;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum MigrationMode {
  FULL,
  ID_DIFF,
  HASH_DIFF;

  public static final List<String> TOKENS = List.of("full", "ids", "hashes");

  public static Optional<MigrationMode> fromToken(String token) {
    return switch (token.toLowerCase(Locale.ROOT)) {
      case "full" -> Optional.of(FULL);
      case "ids" -> Optional.of(ID_DIFF);
      case "hashes" -> Optional.of(HASH_DIFF);
      default -> Optional.empty();
    };
  }
}
