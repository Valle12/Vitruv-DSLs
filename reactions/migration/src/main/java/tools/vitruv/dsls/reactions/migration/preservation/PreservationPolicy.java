package tools.vitruv.dsls.reactions.migration.preservation;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum PreservationPolicy {
  OFF,
  REPORT,
  USER,
  ALL;

  public static final List<String> TOKENS = List.of("off", "report", "user", "all");

  public static Optional<PreservationPolicy> fromToken(String token) {
    return switch (token.toLowerCase(Locale.ROOT)) {
      case "off" -> Optional.of(OFF);
      case "report" -> Optional.of(REPORT);
      case "user" -> Optional.of(USER);
      case "all" -> Optional.of(ALL);
      default -> Optional.empty();
    };
  }

  public boolean analyses() {
    return this != OFF;
  }

  public boolean leavesModelsUnchanged() {
    return this == OFF || this == REPORT;
  }

  public boolean skipsRuleProducedContent() {
    return this != ALL;
  }
}
