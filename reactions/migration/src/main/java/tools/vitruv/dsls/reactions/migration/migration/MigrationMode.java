package tools.vitruv.dsls.reactions.migration.migration;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** How much of a V-SUM a migration rebuilds. */
public enum MigrationMode {
  /** Re-derives every derived model from the dominant one. */
  FULL,
  /** Repropagates only what the rules reach whose identifier was added or removed. */
  ID_DIFF,
  /**
   * Repropagates what the rules reach whose identifier was added or removed and what the rules
   * reach whose semantic hash differs from the recorded one.
   */
  HASH_DIFF;

  /** The tokens the command line accepts, in the order the modes are declared. */
  public static final List<String> TOKENS = List.of("full", "ids", "hashes");

  /**
   * Returns the mode the given token names.
   *
   * @param token a mode token, matched without regard to case
   * @return the named mode, empty when no mode accepts the token
   */
  public static Optional<MigrationMode> fromToken(String token) {
    return switch (token.toLowerCase(Locale.ROOT)) {
      case "full" -> Optional.of(FULL);
      case "ids" -> Optional.of(ID_DIFF);
      case "hashes" -> Optional.of(HASH_DIFF);
      default -> Optional.empty();
    };
  }
}
