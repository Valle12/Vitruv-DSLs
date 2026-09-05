package tools.vitruv.dsls.reactions.migration.strategy;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;

@Slf4j
public final class FewestChangesDominance implements DominanceStrategy {
  private static Set<MetamodelNode> candidatesReachingAllPresent(DominanceContext context) {
    return context.presentNodes().stream()
        .filter(node -> context.graph().reachesAll(node, context.presentNodes()))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static Optional<Long> measuredChangeCount(
      DominanceContext context, MetamodelNode candidate) {
    try {
      long changeCount = context.trialChangeCount(candidate);
      log.info("Proposed changes for dominant {}: {}", candidate.shortName(), changeCount);
      return Optional.of(changeCount);
    } catch (RuntimeException e) {
      log.warn(
          "Skipping candidate {}, trial migration did not complete: {}",
          candidate.shortName(),
          e.getMessage(),
          e);
      return Optional.empty();
    }
  }

  @Override
  public Optional<MetamodelNode> selectDominant(DominanceContext context) {
    Set<MetamodelNode> reachingAll = candidatesReachingAllPresent(context);
    if (reachingAll.isEmpty()) {
      log.warn(
          "No present metamodel can reach all others via propagation; cannot compare proposed"
              + " changes. Present: {}",
          context.presentNodes());
      return Optional.empty();
    }

    MetamodelNode best = null;
    long fewestChanges = Long.MAX_VALUE;
    for (MetamodelNode candidate : reachingAll) {
      Optional<Long> changeCount = measuredChangeCount(context, candidate);
      if (changeCount.isPresent() && changeCount.get() < fewestChanges) {
        fewestChanges = changeCount.get();
        best = candidate;
      }
    }

    if (best == null) {
      log.warn("No candidate could be evaluated by trial migration.");
      return Optional.empty();
    }

    log.info(
        "Selected {} as dominant with the fewest proposed changes ({})",
        best.shortName(),
        fewestChanges);
    return Optional.of(best);
  }
}
