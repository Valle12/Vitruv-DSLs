package tools.vitruv.dsls.reactions.migration.migration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
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
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.BasicMonitor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.EMFCompare;
import org.eclipse.emf.compare.match.impl.MatchEngineFactoryImpl;
import org.eclipse.emf.compare.match.impl.MatchEngineFactoryRegistryImpl;
import org.eclipse.emf.compare.merge.BatchMerger;
import org.eclipse.emf.compare.merge.IMerger;
import org.eclipse.emf.compare.scope.DefaultComparisonScope;
import org.eclipse.emf.compare.utils.UseIdentifiers;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
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
  private static final Set<String> METADATA_EXTENSIONS =
      Set.of("uuid", "models", "correspondence", "marker_vitruv");
  private final SpecificationSource specifications;
  private final AdapterRegistry adapters;
  private final InteractionResultProvider interactionFallback;
  private final int maxRounds;
  private final ModelStateDiff diff =
      new ModelStateDiff(new DefaultStateBasedChangeResolutionStrategy(UseIdentifiers.NEVER));

  private static void mergeStateInto(Resource liveState, Resource desiredState) {
    var matchEngineRegistry = MatchEngineFactoryRegistryImpl.createStandaloneInstance();
    matchEngineRegistry.add(new MatchEngineFactoryImpl(UseIdentifiers.WHEN_AVAILABLE));
    EMFCompare emfCompare =
        EMFCompare.builder().setMatchEngineFactoryRegistry(matchEngineRegistry).build();
    Comparison comparison =
        emfCompare.compare(new DefaultComparisonScope(desiredState, liveState, null));
    new BatchMerger(IMerger.RegistryImpl.createStandaloneInstance())
        .copyAllLeftToRight(comparison.getDifferences(), new BasicMonitor());
  }

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

  private static List<Path> oldDerivedModelFiles(Path derivedOriginalsFolder) {
    if (!Files.isDirectory(derivedOriginalsFolder)) {
      return List.of();
    }

    try (Stream<Path> paths = Files.walk(derivedOriginalsFolder)) {
      return paths.filter(Files::isRegularFile).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static boolean ownedByDerivedNode(Resource resource, List<MetamodelNode> derivedNodes) {
    return resource.getContents().stream()
        .map(root -> root.eClass().getEPackage().getNsURI())
        .anyMatch(nsUri -> derivedNodes.stream().anyMatch(node -> node.owns(nsUri)));
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

  private static String fileExtension(Path path) {
    String name = path.getFileName().toString();
    int dot = name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(dot + 1);
  }

  SourceUpdateOutcome update(
      Path realFolder,
      InternalVirtualModel realVsum,
      PreparedMigration prepared,
      ScratchArea scratch) {
    if (oldDerivedModelFiles(prepared.derivedOriginalsFolder()).isEmpty()) {
      log.info("No original derived models to feed back; the forward re-derivation is final.");
      return SourceUpdateOutcome.skipped();
    }

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
            true, round - 1, false, previousDelta == Long.MAX_VALUE ? -1 : previousDelta);
      }

      List<Resource> scratchState = loadModelStateRebasedTo(scratchFolder, realFolder);
      List<Resource> realState = loadModelState(realFolder);
      long delta = diff.changeCount(realState, scratchState);
      Optional<SourceUpdateOutcome> finished =
          roundOutcome(round, delta, previousDelta, scratchState, realState);
      if (finished.isPresent()) {
        return finished.get();
      }

      commitFilesOntoVsum(
          realVsum,
          modelFiles(scratchFolder),
          scratchFolder,
          realFolder,
          resource -> ownedByDerivedNode(resource, prepared.plan().derived()),
          "application of the counter-propagated state");
      previousDelta = delta;
    }

    log.warn(
        "Source update stopped after the maximum of {} round(s) without converging.", maxRounds);
    return new SourceUpdateOutcome(true, maxRounds, false, previousDelta);
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
      return Optional.of(new SourceUpdateOutcome(true, round - 1, true, 0));
    }

    if (delta >= previousDelta) {
      log.warn(
          "Source update is not converging (delta {} did not shrink below {}); keeping the last"
              + " applied state.",
          delta,
          previousDelta);
      return Optional.of(new SourceUpdateOutcome(true, round - 1, false, delta));
    }

    if (!scratchStateMatchesMetamodels(scratchState, realState)) {
      log.warn(
          "The counter-propagated state lost or gained metamodels; keeping the forward"
              + " re-derivation.");
      return Optional.of(new SourceUpdateOutcome(true, round - 1, false, delta));
    }

    return Optional.empty();
  }

  private void buildScratchMirrorWithOldDerivedState(
      Path realFolder,
      InternalVirtualModel realVsum,
      PreparedMigration prepared,
      Path scratchFolder) {
    List<URI> mirrorUris = nonDerivedFileUris(realVsum, prepared.plan().derived());
    ModelSnapshot mirror =
        ModelSnapshot.of(mirrorUris, adapters).relocatedTo(scratchFolder, realFolder);
    InternalVirtualModel scratchVsum =
        Vsums.build(
            scratchFolder,
            specifications.createSpecifications(),
            new DetectingUserInteraction(interactionFallback));
    try {
      new Replayer(adapters).replayInto(scratchVsum, mirror);
      commitFilesOntoVsum(
          scratchVsum,
          oldDerivedModelFiles(prepared.derivedOriginalsFolder()),
          prepared.derivedOriginalsFolder(),
          scratchFolder,
          resource -> true,
          "backflow of the old derived state");
    } finally {
      scratchVsum.dispose();
    }
  }

  private void commitFilesOntoVsum(
      InternalVirtualModel vsum,
      List<Path> files,
      Path filesBase,
      Path vsumBase,
      Predicate<Resource> include,
      String step) {
    ResourceSet loadSet = newModelResourceSet();
    List<Resource> plainResources = new ArrayList<>();
    List<Resource> recordingResources = new ArrayList<>();
    for (Path file : files) {
      Resource resource = loadSet.getResource(URI.createFileURI(file.toString()), true);
      adapters.adapterFor(resource).normalizeLoadedResource(resource);
      if (!include.test(resource)) {
        continue;
      }

      if (adapters.adapterFor(resource).requiresChangeRecording()) {
        recordingResources.add(resource);
      } else {
        plainResources.add(resource);
      }
    }

    commitViaStateDeriving(vsum, plainResources, filesBase, vsumBase, step);
    commitViaRecordedMerge(vsum, recordingResources, filesBase, vsumBase, step);
  }

  private void commitViaStateDeriving(
      InternalVirtualModel vsum,
      List<Resource> resources,
      Path filesBase,
      Path vsumBase,
      String step) {
    if (resources.isEmpty()) {
      return;
    }

    try (View view =
        Vsums.openViewOf(
            vsum,
            "state-deriving-" + step.hashCode(),
            root -> !adapters.adapterFor(root).requiresChangeRecording())) {
      CommittableView committable =
          view.withChangeDerivingTrait(
              new DefaultStateBasedChangeResolutionStrategy(UseIdentifiers.NEVER));
      Map<String, Resource> liveByFileName = resourcesByFileName(view);
      ResourceSet viewResourceSet = anyResourceSet(liveByFileName.values());
      for (Resource resource : resources) {
        prepareForView(resource, viewResourceSet);
        List<EObject> nextContent = new ArrayList<>(EcoreUtil.copyAll(resource.getContents()));
        Resource live = liveByFileName.get(resource.getURI().lastSegment());
        if (live != null) {
          live.getContents().clear();
          live.getContents().addAll(nextContent);
        } else if (!nextContent.isEmpty()) {
          registerRootsAt(committable, nextContent, targetUriFor(resource, filesBase, vsumBase));
        }
      }

      commitUnlessNothingChanged(committable, step);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Closing the view for the " + step + " failed", e);
    }
  }

  private void commitViaRecordedMerge(
      InternalVirtualModel vsum,
      List<Resource> resources,
      Path filesBase,
      Path vsumBase,
      String step) {
    if (resources.isEmpty()) {
      return;
    }

    try (View view = Vsums.openViewOfAll(vsum, "recorded-merge-" + step.hashCode())) {
      CommittableView committable = view.withChangeRecordingTrait();
      Map<String, Resource> liveByFileName = resourcesByFileName(view);
      ResourceSet viewResourceSet = anyResourceSet(liveByFileName.values());
      for (Resource resource : resources) {
        prepareForView(resource, viewResourceSet);
        Resource live = liveByFileName.get(resource.getURI().lastSegment());
        if (live != null) {
          mergeStateInto(live, resource);
        } else if (!resource.getContents().isEmpty()) {
          registerRootsAt(
              committable,
              new ArrayList<>(EcoreUtil.copyAll(resource.getContents())),
              targetUriFor(resource, filesBase, vsumBase));
        }
      }

      commitUnlessNothingChanged(committable, step);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Closing the view for the " + step + " failed", e);
    }
  }

  private void prepareForView(Resource resource, ResourceSet viewResourceSet) {
    if (viewResourceSet != null) {
      adapters.adapterFor(resource).prepareForReplayInto(viewResourceSet, resource);
    }
  }

  private List<URI> nonDerivedFileUris(
      InternalVirtualModel vsum, List<MetamodelNode> derivedNodes) {
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
        if (uri.isFile() && !adapters.isPlatformLibraryResource(uri) && !ownedByDerivedNode) {
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

  private List<Resource> loadModelState(Path folder) {
    ResourceSet resourceSet = newModelResourceSet();
    return modelFiles(folder).stream()
        .map(
            file -> {
              Resource resource = resourceSet.getResource(URI.createFileURI(file.toString()), true);
              adapters.adapterFor(resource).normalizeLoadedResource(resource);
              return resource;
            })
        .toList();
  }

  private List<Resource> loadModelStateRebasedTo(Path folder, Path uriBaseFolder) {
    ResourceSet resourceSet = newModelResourceSet();
    List<Resource> resources = new ArrayList<>();
    for (Path file : modelFiles(folder)) {
      URI rebased =
          URI.createFileURI(uriBaseFolder.resolve(folder.relativize(file).toString()).toString());
      Resource resource = resourceSet.createResource(rebased);
      try (var content = Files.newInputStream(file)) {
        resource.load(content, resourceSet.getLoadOptions());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }

      adapters.adapterFor(resource).normalizeLoadedResource(resource);
      resources.add(resource);
    }

    return resources;
  }

  private List<Path> modelFiles(Path folder) {
    Path vsumMetadataFolder = folder.resolve("vsum");
    Path consistencyMetadataFolder = folder.resolve("consistencymetadata");
    try (Stream<Path> paths = Files.walk(folder)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(path -> !path.startsWith(vsumMetadataFolder))
          .filter(path -> !path.startsWith(consistencyMetadataFolder))
          .filter(path -> !METADATA_EXTENSIONS.contains(fileExtension(path)))
          .filter(path -> !adapters.isPlatformLibraryResource(URI.createFileURI(path.toString())))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private ResourceSet newModelResourceSet() {
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet.getLoadOptions().putAll(adapters.combinedLoadOptions());
    return resourceSet;
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

      diff.between(scratchResource, realResource)
          .getEChanges()
          .forEach(
              change ->
                  log.debug("  delta[{}]: {}", scratchResource.getURI().lastSegment(), change));
    }
  }
}
