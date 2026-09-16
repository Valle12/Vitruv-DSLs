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

/**
 * Derives a whole V-SUM from one candidate metamodel in a scratch folder and counts how far
 * the result differs from what the V-SUM holds today. The fewest changes strategy compares its
 * candidates this way, without the V-SUM being migrated ever being touched.
 */
@RequiredArgsConstructor
public class TrialMigration {
  private final SpecificationSource specifications;
  private final AdapterRegistry adapters;
  private final InteractionResultProvider interactionFallback;
  private final ScratchArea scratch;
  private final Path realFolder;
  private final ModelStateDiff diff = new ModelStateDiff();

  /**
   * Runs a trial with the given candidate as the dominant model. Only the models the candidate
   * does not own are compared, since the ones it owns are carried over unchanged anyway.
   *
   * @param candidate the metamodel to derive everything else from
   * @param candidateResourceUris the resources that candidate owns
   * @return how many changes the trial proposed and where its V-SUM was built
   */
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

  /**
   * What a trial migration cost and which scratch folder it left its V-SUM in.
   *
   * @param changeCount how many changes the trial proposed
   * @param folder the scratch folder its V-SUM was built in
   */
  public record Outcome(long changeCount, Path folder) {}
}
