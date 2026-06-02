package tools.vitruv.migration;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class ExplicitDominanceStrategy implements DominanceStrategy {
  // TODO maybe use enum
  private final String token;

  public ExplicitDominanceStrategy(String token) {
    this.token = token.toLowerCase(Locale.ROOT);
  }

  @Override
  public Optional<Set<String>> selectDominant(DominanceContext context) {
    return context.presentNodes().stream()
        .filter(
            node -> node.stream().anyMatch(nsUri -> nsUri.toLowerCase(Locale.ROOT).contains(token)))
        .findFirst();
  }
}
