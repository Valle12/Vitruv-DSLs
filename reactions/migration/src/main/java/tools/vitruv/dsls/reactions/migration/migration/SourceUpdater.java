package tools.vitruv.dsls.reactions.migration.migration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.compare.utils.UseIdentifiers;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import tools.vitruv.change.interaction.InteractionResultProvider;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;
import tools.vitruv.dsls.reactions.migration.interaction.DetectingUserInteraction;
import tools.vitruv.dsls.reactions.migration.spec.SpecificationSource;
import tools.vitruv.dsls.reactions.migration.vsum.ModelSnapshot;
import tools.vitruv.dsls.reactions.migration.vsum.ScratchArea;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.changederivation.DefaultStateBasedChangeResolutionStrategy;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

@Slf4j
@RequiredArgsConstructor
class SourceUpdater {
  private final SpecificationSource specifications;
  private final AdapterRegistry adapters;
  private final InteractionResultProvider interactionFallback;
  private final int maxRounds;
  private final ModelStateDiff diff = new ModelStateDiff();

  private final List<String> retracted = new ArrayList<>();

  @SuppressWarnings("UnstableApiUsage")
  private static void registerRootsAt(
      CommittableView committable, List<EObject> roots, URI target) {
    committable.registerRoot(roots.getFirst(), target);
    Resource created = roots.getFirst().eResource();
    for (int i = 1; i < roots.size(); i++) {
      created.getContents().add(roots.get(i));
    }
  }

  private static URI targetUriFor(Resource resource, Path filesBase, Path vsumBase) {
    Path file = Path.of(resource.getURI().toFileString());
    return URI.createFileURI(vsumBase.resolve(filesBase.relativize(file).toString()).toString());
  }

  private static List<Resource> sourceOwned(
      List<Resource> resources, List<MetamodelNode> derivedNodes) {
    return ModelStates.notOwnedBy(resources, ModelStates.ownedByAny(derivedNodes));
  }

  private static boolean scratchStateMatchesMetamodels(
      List<Resource> scratchState, List<Resource> realState) {
    return rootNsUris(scratchState).containsAll(rootNsUris(realState));
  }

  private static Set<String> rootNsUris(List<Resource> resources) {
    return resources.stream()
        .flatMap(resource -> resource.getContents().stream())
        .map(root -> root.eClass().getEPackage().getNsURI())
        .collect(Collectors.toUnmodifiableSet());
  }

  private static Map<String, Resource> resourcesByFileName(View view) {
    Map<String, Resource> byFileName = new LinkedHashMap<>();
    for (EObject root : view.getRootObjects()) {
      Resource resource = root.eResource();
      if (resource != null && resource.getURI() != null) {
        byFileName.putIfAbsent(resource.getURI().lastSegment(), resource);
      }
    }

    return byFileName;
  }

  private static ResourceSet anyResourceSet(Iterable<Resource> resources) {
    for (Resource resource : resources) {
      if (resource.getResourceSet() != null) {
        return resource.getResourceSet();
      }
    }

    return null;
  }

  private static void commitUnlessNothingChanged(CommittableView committable, String step) {
    try {
      committable.commitChanges();
    } catch (IllegalArgumentException noConcreteChange) {
      log.info("The {} produced no changes.", step);
    }
  }

  private static CommittableView committableView(View view, boolean recordChanges) {
    return recordChanges
        ? view.withChangeRecordingTrait()
        : view.withChangeDerivingTrait(
            new DefaultStateBasedChangeResolutionStrategy(UseIdentifiers.NEVER));
  }

  SourceUpdateOutcome update(
      Path realFolder,
      InternalVirtualModel realVsum,
      PreparedMigration prepared,
      ScratchArea scratch) {
    retracted.clear();
    if (prepared.oldDerivedFiles().isEmpty()) {
      log.info("No original derived models to feed back; the forward re-derivation is final.");
      return SourceUpdateOutcome.skipped();
    }

    List<MetamodelNode> derivedNodes = prepared.plan().derived();
    long previousDelta = Long.MAX_VALUE;
    for (int round = 1; round <= maxRounds; round++) {
      Path scratchFolder = scratch.newFolder("source-update-" + round);
      try {
        buildScratchMirrorWithOldDerivedState(realFolder, realVsum, prepared, scratchFolder);
      } catch (RuntimeException e) {
        log.warn(
            "The counter-propagation could not complete in the scratch VSUM ({}); keeping the"
                + " forward re-derivation.",
            e.toString());
        return new SourceUpdateOutcome(
            true,
            round - 1,
            false,
            previousDelta == Long.MAX_VALUE ? -1 : previousDelta,
            List.copyOf(retracted));
      }

      List<Resource> scratchState =
          sourceOwned(ModelStates.loadRebased(scratchFolder, realFolder, adapters), derivedNodes);
      List<Resource> realState = sourceOwned(ModelStates.load(realFolder, adapters), derivedNodes);
      long delta = SourceStateMerge.count(realState, scratchState).total();
      Optional<SourceUpdateOutcome> finished =
          roundOutcome(round, delta, previousDelta, scratchState, realState);
      if (finished.isPresent()) {
        return finished.get();
      }

      commitResourcesOntoVsum(
          realVsum,
          scratchState,
          realFolder,
          realFolder,
          "application of the counter-propagated source state");
      previousDelta = delta;
    }

    log.warn(
        "Source update stopped after the maximum of {} round(s) without converging.", maxRounds);
    return new SourceUpdateOutcome(true, maxRounds, false, previousDelta, List.copyOf(retracted));
  }

  private Optional<SourceUpdateOutcome> roundOutcome(
      int round,
      long delta,
      long previousDelta,
      List<Resource> scratchState,
      List<Resource> realState) {
    log.info(
        "Source-update round {}: {} proposed change(s) against the current state", round, delta);
    if (log.isDebugEnabled() && delta > 0 && delta <= 50) {
      logDeltaDetails(realState, scratchState);
    }

    if (delta == 0) {
      return Optional.of(new SourceUpdateOutcome(true, round - 1, true, 0, List.copyOf(retracted)));
    }

    if (delta >= previousDelta) {
      log.warn(
          "Source update is not converging (delta {} did not shrink below {}); keeping the last"
              + " applied state.",
          delta,
          previousDelta);
      return Optional.of(
          new SourceUpdateOutcome(true, round - 1, false, delta, List.copyOf(retracted)));
    }

    if (!scratchStateMatchesMetamodels(scratchState, realState)) {
      log.warn(
          "The counter-propagated state lost or gained metamodels; keeping the forward"
              + " re-derivation.");
      return Optional.of(
          new SourceUpdateOutcome(true, round - 1, false, delta, List.copyOf(retracted)));
    }

    return Optional.empty();
  }

  private void buildScratchMirrorWithOldDerivedState(
      Path realFolder,
      InternalVirtualModel realVsum,
      PreparedMigration prepared,
      Path scratchFolder) {
    List<URI> mirrorUris = nonDerivedFileUris(realVsum, prepared.plan().derived(), realFolder);
    ModelSnapshot mirror =
        ModelSnapshot.of(mirrorUris, adapters, realFolder).relocatedTo(scratchFolder, realFolder);
    InternalVirtualModel scratchVsum =
        Vsums.build(
            scratchFolder,
            specifications.createSpecifications(),
            new DetectingUserInteraction(interactionFallback));
    try {
      new Replayer(adapters).replayInto(scratchVsum, mirror);
      commitFilesOntoVsum(
          scratchVsum,
          prepared.oldDerivedFiles(),
          prepared.preMigrationFolder(),
          scratchFolder,
          resource -> true);
    } finally {
      scratchVsum.dispose();
    }
  }

  private void commitFilesOntoVsum(
      InternalVirtualModel vsum,
      List<Path> files,
      Path filesBase,
      Path vsumBase,
      Predicate<Resource> include) {
    ResourceSet loadSet = adapters.newResourceSet();
    List<Resource> included = new ArrayList<>();
    for (Path file : files) {
      Resource resource = adapters.load(loadSet, URI.createFileURI(file.toString()));
      if (include.test(resource)) {
        included.add(resource);
      }
    }

    commitResourcesOntoVsum(
        vsum, included, filesBase, vsumBase, "backflow of the old derived state");
  }

  private void commitResourcesOntoVsum(
      InternalVirtualModel vsum,
      List<Resource> desiredStates,
      Path filesBase,
      Path vsumBase,
      String step) {
    List<Resource> plainResources = new ArrayList<>();
    List<Resource> recordingResources = new ArrayList<>();
    for (Resource resource : desiredStates) {
      if (adapters.adapterFor(resource).requiresChangeRecording()) {
        recordingResources.add(resource);
      } else {
        plainResources.add(resource);
      }
    }

    commitAdditively(vsum, plainResources, filesBase, vsumBase, step, false);
    commitAdditively(vsum, recordingResources, filesBase, vsumBase, step, true);
  }

  private void commitAdditively(
      InternalVirtualModel vsum,
      List<Resource> desiredStates,
      Path filesBase,
      Path vsumBase,
      String step,
      boolean recordChanges) {
    if (desiredStates.isEmpty()) {
      return;
    }

    try (View view = openViewFor(vsum, step, recordChanges)) {
      CommittableView committable = committableView(view, recordChanges);
      Map<String, Resource> liveByFileName = resourcesByFileName(view);
      ResourceSet viewResourceSet = anyResourceSet(liveByFileName.values());
      for (Resource desiredState : desiredStates) {
        prepareForView(desiredState, viewResourceSet);
        Resource live = liveByFileName.get(desiredState.getURI().lastSegment());
        if (live != null) {
          SourceStateMerge.Delta delta = SourceStateMerge.between(live, desiredState);
          retracted.addAll(delta.describeRetractions());
          delta.apply();
        } else if (!desiredState.getContents().isEmpty()) {
          registerRootsAt(
              committable,
              new ArrayList<>(EcoreUtil.copyAll(desiredState.getContents())),
              targetUriFor(desiredState, filesBase, vsumBase));
        }
      }

      commitUnlessNothingChanged(committable, step);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Closing the view for the " + step + " failed", e);
    }
  }

  private View openViewFor(InternalVirtualModel vsum, String step, boolean recordChanges) {
    String name = "additive-merge-" + step.hashCode();
    return recordChanges
        ? Vsums.openViewOfAll(vsum, name)
        : Vsums.openViewOf(
            vsum, name, root -> !adapters.adapterFor(root).requiresChangeRecording());
  }

  private void prepareForView(Resource resource, ResourceSet viewResourceSet) {
    if (viewResourceSet != null) {
      adapters.adapterFor(resource).prepareForReplayInto(viewResourceSet, resource.getContents());
    }
  }

  private List<URI> nonDerivedFileUris(
      InternalVirtualModel vsum, List<MetamodelNode> derivedNodes, Path realFolder) {
    Set<URI> uris = new LinkedHashSet<>();
    try (View view = Vsums.openViewOfAll(vsum, "source-update-mirror")) {
      for (EObject root : view.getRootObjects()) {
        Resource resource = root.eResource();
        if (resource == null || resource.getURI() == null) {
          continue;
        }

        URI uri = resource.getURI();
        String nsUri = root.eClass().getEPackage().getNsURI();
        boolean ownedByDerivedNode = derivedNodes.stream().anyMatch(node -> node.owns(nsUri));
        if (adapters.isMigratedModel(uri, realFolder) && !ownedByDerivedNode) {
          uris.add(uri);
        }
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to collect the models to mirror", e);
    }

    return List.copyOf(uris);
  }

  private void logDeltaDetails(List<Resource> realState, List<Resource> scratchState) {
    Map<String, Resource> realByName = new LinkedHashMap<>();
    realState.forEach(r -> realByName.put(r.getURI().lastSegment(), r));
    for (Resource scratchResource : scratchState) {
      Resource realResource = realByName.get(scratchResource.getURI().lastSegment());
      if (realResource == null) {
        log.debug("  delta: scratch-only resource {}", scratchResource.getURI().lastSegment());
        continue;
      }

      String name = scratchResource.getURI().lastSegment();
      try {
        diff.changesBetween(scratchResource, realResource)
            .forEach(change -> log.debug("  delta[{}] change: {}", name, change));
      } catch (RuntimeException e) {
        log.debug("  delta[{}] listing failed: {}", name, e.getMessage());
      }
    }
  }
}
