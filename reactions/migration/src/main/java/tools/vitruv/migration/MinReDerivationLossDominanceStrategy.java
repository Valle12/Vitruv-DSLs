package tools.vitruv.migration;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class MinReDerivationLossDominanceStrategy implements DominanceStrategy {
  @Override
  public Optional<Set<String>> selectDominant(DominanceContext context) {
    List<Set<String>> reachingAll =
        context.presentNodes().stream()
            .filter(node -> context.graph().reachesAll(node, context.presentNodes()))
            .toList();
    if (reachingAll.isEmpty()) {
      log.warn(
          "No present metamodel can reach all others via propagation; cannot measure"
              + " re-derivation loss. Present: {}",
          context.presentNodes());
      return Optional.empty();
    }

    Set<String> best = null;
    long bestLoss = Long.MAX_VALUE;
    for (Set<String> candidate : reachingAll) {
      try {
        long loss = context.reDerivationLoss(candidate);
        log.info("Re-derivation loss for {}: {}", PropagationGraph.shortName(candidate), loss);
        if (loss < bestLoss) {
          bestLoss = loss;
          best = candidate;
        }
      } catch (RuntimeException e) {
        log.warn(
            "Skipping candidate {} - trial re-derivation did not complete: {}",
            PropagationGraph.shortName(candidate),
            e.getMessage());
      }
    }

    if (best == null) {
      log.warn("No candidate could be evaluated empirically.");
      return Optional.empty();
    }

    log.info(
        "Selected {} as dominant with minimal loss {}", PropagationGraph.shortName(best), bestLoss);
    return Optional.of(best);
  }
}
