package tools.vitruv.dsls.reactions.migration.migration;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
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

  public long changeCountFor(
      MetamodelNode candidate,
      Map<String, List<EObject>> currentRootsByNsUri,
      List<URI> candidateResourceUris) {
    Path trialFolder = scratch.newFolder("trial");
    ModelSnapshot snapshot =
        ModelSnapshot.of(candidateResourceUris, adapters).relocatedTo(trialFolder, realFolder);
    InternalVirtualModel trialVsum =
        Vsums.build(
            trialFolder,
            specifications.createSpecifications(),
            new DetectingUserInteraction(interactionFallback));
    try {
      new Replayer(adapters).replayInto(trialVsum, snapshot);
      Map<String, List<EObject>> regenerated = ModelGroups.byNsUri(Vsums.readRoots(trialVsum));
      return proposedChangesForOtherNodes(candidate, currentRootsByNsUri, regenerated);
    } finally {
      trialVsum.dispose();
    }
  }

  private long proposedChangesForOtherNodes(
      MetamodelNode candidate,
      Map<String, List<EObject>> current,
      Map<String, List<EObject>> regenerated) {
    long changes = 0;
    for (Map.Entry<String, List<EObject>> entry : current.entrySet()) {
      if (candidate.owns(entry.getKey())) {
        continue;
      }

      changes +=
          diff.changeCount(
              ModelGroups.resourcesOf(entry.getValue()),
              ModelGroups.resourcesOf(regenerated.getOrDefault(entry.getKey(), List.of())));
    }

    return changes;
  }
}
