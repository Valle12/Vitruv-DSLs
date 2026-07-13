package tools.vitruv.dsls.reactions.migration.strategy;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum StrategyChoice {
  EXPLICIT("explicit"),
  REACHABILITY("reachability"),
  FEWEST_CHANGES("derivationLoss", "fewestChanges");

  private final List<String> tokens;

  StrategyChoice(String... tokens) {
    this.tokens = List.of(tokens);
  }

  public static Optional<StrategyChoice> fromToken(String token) {
    return Arrays.stream(values()).filter(choice -> choice.matches(token)).findFirst();
  }

  private boolean matches(String token) {
    return tokens.stream().anyMatch(known -> known.equalsIgnoreCase(token));
  }
}
