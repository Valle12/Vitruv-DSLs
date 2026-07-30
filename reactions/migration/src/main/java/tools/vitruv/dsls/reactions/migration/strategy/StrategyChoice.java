package tools.vitruv.dsls.reactions.migration.strategy;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum StrategyChoice {
  EXPLICIT("explicit"),
  REACHABILITY("reachability"),
  FEWEST_CHANGES("derivationLoss", "fewestChanges");

  public static final List<String> TOKENS =
      Arrays.stream(values()).flatMap(choice -> choice.acceptedTokens.stream()).toList();

  private final List<String> acceptedTokens;

  StrategyChoice(String... acceptedTokens) {
    this.acceptedTokens = List.of(acceptedTokens);
  }

  public static Optional<StrategyChoice> fromToken(String token) {
    return Arrays.stream(values()).filter(choice -> choice.matches(token)).findFirst();
  }

  private boolean matches(String token) {
    return acceptedTokens.stream().anyMatch(known -> known.equalsIgnoreCase(token));
  }
}
