package tools.vitruv.dsls.reactions.migration.migration;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.eclipse.emf.common.util.URI;
import tools.vitruv.change.interaction.InteractionResultProvider;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;
import tools.vitruv.dsls.reactions.migration.interaction.DetectingUserInteraction;
import tools.vitruv.dsls.reactions.migration.spec.SpecificationSource;
import tools.vitruv.dsls.reactions.migration.vsum.ModelSnapshot;
import tools.vitruv.dsls.reactions.migration.vsum.ScratchArea;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

@RequiredArgsConstructor
public class TrialMigration {
  private final SpecificationSource specifications;
  private final AdapterRegistry adapters;
  private final InteractionResultProvider interactionFallback;
  private final ScratchArea scratch;
  private final Path realFolder;
  private final ModelStateDiff diff = new ModelStateDiff();

  public Outcome run(MetamodelNode candidate, List<URI> candidateResourceUris) {
    Path trialFolder = scratch.newFolder("trial");
    ModelSnapshot snapshot =
        ModelSnapshot.of(candidateResourceUris, adapters, realFolder)
            .relocatedTo(trialFolder, realFolder);
    InternalVirtualModel trialVsum =
        Vsums.build(
            trialFolder,
            specifications.createSpecifications(),
            new DetectingUserInteraction(interactionFallback));
    try {
      new Replayer(adapters).replayInto(trialVsum, snapshot);
    } finally {
      trialVsum.dispose();
    }

    Predicate<String> ownedByCandidate = candidate::owns;
    long changeCount =
        ModelStates.changeCount(
            diff,
            adapters,
            ModelStates.notOwnedBy(ModelStates.load(realFolder, adapters), ownedByCandidate),
            ModelStates.notOwnedBy(
                ModelStates.loadRebased(trialFolder, realFolder, adapters), ownedByCandidate));
    return new Outcome(changeCount, trialFolder);
  }

  public record Outcome(long changeCount, Path folder) {}
}
