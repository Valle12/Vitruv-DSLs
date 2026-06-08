package tools.vitruv.strategy;

import java.util.Optional;
import java.util.Set;

public class ExplicitDominanceStrategy implements DominanceStrategy {
  private final String token;

  public ExplicitDominanceStrategy(String token) {
    this.token = token.toLowerCase();
  }

  @Override
  public Optional<Set<String>> selectDominant(DominanceContext context) {
    return context.presentNodes().stream()
        .filter(node -> node.stream().anyMatch(nsUri -> nsUri.toLowerCase().contains(token)))
        .findFirst();
  }
}
