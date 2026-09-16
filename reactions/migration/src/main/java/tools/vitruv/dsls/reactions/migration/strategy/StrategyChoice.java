package tools.vitruv.dsls.reactions.migration.strategy;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** The dominance strategies the command line offers. */
public enum StrategyChoice {
  /** Takes the dominant model from the metamodel token the caller passes. */
  EXPLICIT("explicit"),
  /** Takes the metamodel with the widest reach through propagation. */
  REACHABILITY("reachability"),
  /** Takes the metamodel whose trial migration proposes the fewest changes. */
  FEWEST_CHANGES("derivationLoss", "fewestChanges");

  /** Every token the command line accepts, in the order the strategies are declared. */
  public static final List<String> TOKENS =
      Arrays.stream(values()).flatMap(choice -> choice.acceptedTokens.stream()).toList();

  private final List<String> acceptedTokens;

  StrategyChoice(String... acceptedTokens) {
    this.acceptedTokens = List.of(acceptedTokens);
  }

  /**
   * Returns the strategy the given token names.
   *
   * @param token a strategy token, matched without regard to case
   * @return the named strategy, empty when no strategy accepts the token
   */
  public static Optional<StrategyChoice> fromToken(String token) {
    return Arrays.stream(values()).filter(choice -> choice.matches(token)).findFirst();
  }

  private boolean matches(String token) {
    return acceptedTokens.stream().anyMatch(known -> known.equalsIgnoreCase(token));
  }
}
