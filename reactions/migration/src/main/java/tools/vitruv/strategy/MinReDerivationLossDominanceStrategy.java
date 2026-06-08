package tools.vitruv.strategy;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import tools.vitruv.migration.PropagationGraph;

@Slf4j
public final class MinReDerivationLossDominanceStrategy implements DominanceStrategy {
  @Override
  public Optional<Set<String>> selectDominant(DominanceContext context) {
    Set<Set<String>> reachingAll =
        context.presentNodes().stream()
            .filter(node -> context.graph().reachesAll(node, context.presentNodes()))
            .collect(Collectors.toSet());
    if (reachingAll.isEmpty()) {
      log.warn(
          "No present metamodel can reach all others via propagation; cannot measure"
              + " re-derivation loss. Present: {}",
          context.presentNodes());
      return Optional.empty();
    }

    Set<String> best = null;
    long minLoss = Long.MAX_VALUE;
    for (Set<String> candidate : reachingAll) {
      try {
        long loss = context.reDerivationLoss(candidate);
        log.info("Re-derivation loss for {}: {}", PropagationGraph.shortName(candidate), loss);
        if (loss < minLoss) {
          minLoss = loss;
          best = candidate;
        }
      } catch (RuntimeException e) {
        log.warn(
            "Skipping candidate {}, trial re-derivation did not complete: {}",
            PropagationGraph.shortName(candidate),
            e.getMessage());
      }
    }

    if (best == null) {
      log.warn("No candidate could be evaluated empirically.");
      return Optional.empty();
    }

    log.info(
        "Selected {} as dominant model with minimal loss {}",
        PropagationGraph.shortName(best),
        minLoss);
    return Optional.of(best);
  }
}
