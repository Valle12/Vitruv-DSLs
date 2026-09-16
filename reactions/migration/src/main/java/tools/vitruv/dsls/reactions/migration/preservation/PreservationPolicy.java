package tools.vitruv.dsls.reactions.migration.preservation;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** How far a migration carries over content that no consistency rule produced. */
public enum PreservationPolicy {
  /** Carries nothing over and writes no report. */
  OFF,
  /** Works out what could be carried over and reports it, without changing a model. */
  REPORT,
  /** Carries over the content a person wrote, as far as the new metamodels allow. */
  USER,
  /**
   * Carries over every underived value, including the content that the new rules no longer
   * derive by themselves.
   */
  ALL;

  /** The tokens the command line accepts, in the order the policies are declared. */
  public static final List<String> TOKENS = List.of("off", "report", "user", "all");

  /**
   * Returns the policy the given token names.
   *
   * @param token a policy token, matched without regard to case
   * @return the named policy, empty when no policy accepts the token
   */
  public static Optional<PreservationPolicy> fromToken(String token) {
    return switch (token.toLowerCase(Locale.ROOT)) {
      case "off" -> Optional.of(OFF);
      case "report" -> Optional.of(REPORT);
      case "user" -> Optional.of(USER);
      case "all" -> Optional.of(ALL);
      default -> Optional.empty();
    };
  }

  /**
   * Returns whether the preservation step runs at all under this policy.
   *
   * @return whether anything is worked out
   */
  public boolean analyses() {
    return this != OFF;
  }

  /**
   * Returns whether the step only reports instead of putting content back.
   *
   * @return whether the models stay as the migration left them
   */
  public boolean leavesModelsUnchanged() {
    return this == OFF || this == REPORT;
  }

  /**
   * Returns whether content that a consistency rule produced is passed over.
   *
   * @return whether only content no rule produced is carried over
   */
  public boolean skipsRuleProducedContent() {
    return this != ALL;
  }
}
