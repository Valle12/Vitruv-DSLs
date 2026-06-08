package tools.vitruv.migration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import tools.vitruv.change.interaction.InteractionResultProvider;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.ViewProvider;
import tools.vitruv.framework.views.ViewSelector;
import tools.vitruv.framework.views.ViewType;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;
import tools.vitruv.interaction.DetectingUserInteraction;
import tools.vitruv.interaction.UserInteractionRequiredException;
import tools.vitruv.strategy.DominanceContext;
import tools.vitruv.strategy.DominanceStrategy;

@Slf4j
@RequiredArgsConstructor
public class MigrationRunner {
  private static final String IGNORE_FORMATTING = "DISABLE_LAYOUT_INFORMATION_RECORDING";
  private static final String JAMOPP_NS_PREFIX = "http://www.emftext.org/java";
  private final List<ChangePropagationSpecification> specifications;
  private final PropagationGraph graph;
  private final DominanceStrategy dominanceStrategy;
  private final InteractionResultProvider reDerivationInteractionFallback;
  private final StateBasedDiff diff = new StateBasedDiff();
  private Path scratchFolder;
  private int trialCounter = 0;

  public MigrationRunner(
      List<ChangePropagationSpecification> specifications,
      PropagationGraph graph,
      DominanceStrategy dominanceStrategy) {
    this(specifications, graph, dominanceStrategy, null);
  }

  private static List<URI> dominantResourceUris(ViewProvider provider, Set<String> node) {
    View view = viewOfAll(provider, "dominant-uris");
    try {
      return view.getRootObjects().stream()
          .filter(root -> PropagationGraph.nodeOwns(node, root.eClass().getEPackage().getNsURI()))
          .map(root -> root.eResource().getURI())
          .distinct()
          .toList();
    } finally {
      closeQuietly(view);
    }
  }

  private static List<URI> allResourceUris(ViewProvider provider) {
    View view = viewOfAll(provider, "all-uris");
    try {
      return view.getRootObjects().stream()
          .map(root -> root.eResource().getURI())
          .distinct()
          .toList();
    } finally {
      closeQuietly(view);
    }
  }

  private static Map<String, List<EObject>> groupByNsUri(List<EObject> roots) {
    return roots.stream()
        .collect(
            Collectors.groupingBy(
                root -> root.eClass().getEPackage().getNsURI(),
                LinkedHashMap::new,
                Collectors.toList()));
  }

  private static Set<Resource> resourcesOf(List<EObject> roots) {
    Set<Resource> resources = new LinkedHashSet<>();
    for (EObject root : roots) {
      if (root.eResource() != null) {
        resources.add(root.eResource());
      }
    }

    return resources;
  }

  private static List<EObject> readRoots(ViewProvider provider) {
    View view = viewOfAll(provider, "read-all");
    try {
      return new ArrayList<>(view.getRootObjects());
    } finally {
      closeQuietly(view);
    }
  }

  private static void closeQuietly(View view) {
    try {
      view.close();
    } catch (Exception e) {
      log.warn("Failed to close view", e);
    }
  }

  private static View viewOfAll(ViewProvider provider, String name) {
    ViewType<? extends ViewSelector> viewType = ViewTypeFactory.createIdentityMappingViewType(name);
    ViewSelector selector = provider.createSelector(viewType);
    selector.getSelectableElements().forEach(element -> selector.setSelected(element, true));
    return selector.createView();
  }

  private static View emptyView(ViewProvider provider) {
    ViewType<? extends ViewSelector> viewType =
        ViewTypeFactory.createIdentityMappingViewType("migration-source");
    ViewSelector selector = provider.createSelector(viewType);
    return selector.createView();
  }

  private static URI persistenceUriFor(URI sourceUri, Path folder) {
    String fileName =
        sourceUri != null && sourceUri.lastSegment() != null
            ? sourceUri.lastSegment()
            : "model.model";
    return URI.createFileURI(folder.resolve(fileName).toString());
  }

  private static void clearVsumMetadata(Path modelFolder) throws IOException {
    Set<String> metadataFiles =
        Set.of("uuid.uuid", "models.models", "correspondences.correspondence");
    List<Path> toDelete;
    try (Stream<Path> paths = Files.walk(modelFolder)) {
      toDelete =
          paths
              .filter(Files::isRegularFile)
              .filter(path -> metadataFiles.contains(path.getFileName().toString()))
              .toList();
    }

    for (Path path : toDelete) {
      Files.deleteIfExists(path);
      log.info("Cleared stale VSUM metadata {} before re-derivation", path.getFileName());
    }
  }

  private static boolean isJdkStandardLibraryResource(URI uri) {
    String path = uri.toString();
    return path.contains("/java/")
        || path.contains("/javax/")
        || path.contains("/sun/")
        || path.contains("/jdk/");
  }

  private static void deleteFiles(List<URI> fileUris) throws IOException {
    for (URI uri : fileUris) {
      Path path = Path.of(uri.toFileString());
      if (Files.deleteIfExists(path)) {
        log.info("Removed old model file {} before re-derivation", path);
      }
    }
  }

  static List<CrossResourceReference> detachCrossResourceReferences(
      Map<URI, List<EObject>> rootsByUri) {
    Map<EObject, URI> ownerUri = new IdentityHashMap<>();
    for (Map.Entry<URI, List<EObject>> entry : rootsByUri.entrySet()) {
      for (EObject root : entry.getValue()) {
        ownerUri.put(root, entry.getKey());
        root.eAllContents().forEachRemaining(object -> ownerUri.put(object, entry.getKey()));
      }
    }

    List<CrossResourceReference> detached = new ArrayList<>();
    for (Map.Entry<URI, List<EObject>> entry : rootsByUri.entrySet()) {
      for (EObject root : entry.getValue()) {
        detachCrossResourceReferences(root, entry.getKey(), ownerUri, detached);
        root.eAllContents()
            .forEachRemaining(
                object ->
                    detachCrossResourceReferences(object, entry.getKey(), ownerUri, detached));
      }
    }

    return detached;
  }

  private static void detachCrossResourceReferences(
      EObject holder,
      URI ownerUri,
      Map<EObject, URI> uriOf,
      List<CrossResourceReference> detached) {
    for (EReference feature : holder.eClass().getEAllReferences()) {
      if (feature.isContainment()
          || feature.isContainer()
          || !feature.isChangeable()
          || feature.isDerived()) {
        continue;
      }

      if (feature.isMany()) {
        List<EObject> values = (List<EObject>) holder.eGet(feature);
        for (EObject target : List.copyOf(values)) {
          if (isInOtherResource(target, ownerUri, uriOf)) {
            detached.add(new CrossResourceReference(holder, feature, target));
            values.remove(target);
          }
        }
      } else if (holder.eGet(feature) instanceof EObject target
          && isInOtherResource(target, ownerUri, uriOf)) {
        detached.add(new CrossResourceReference(holder, feature, target));
        holder.eUnset(feature);
      }
    }
  }

  private static boolean isInOtherResource(EObject target, URI ownerUri, Map<EObject, URI> uriOf) {
    URI targetUri = uriOf.get(target);
    return targetUri != null && !targetUri.equals(ownerUri);
  }

  public static void reattachCrossResourceReference(CrossResourceReference reference) {
    if (reference.feature().isMany()) {
      ((List<EObject>) reference.holder().eGet(reference.feature())).add(reference.target());
    } else {
      reference.holder().eSet(reference.feature(), reference.target());
    }
  }

  private static Map<URI, List<EObject>> groupRootsByUri(List<EObject> roots, List<URI> uris) {
    Map<URI, List<EObject>> rootsByUri = new LinkedHashMap<>();
    for (int i = 0; i < roots.size(); i++) {
      rootsByUri.computeIfAbsent(uris.get(i), key -> new ArrayList<>()).add(roots.get(i));
    }

    return rootsByUri;
  }

  public void run(Path sourceFolder) throws IOException {
    try {
      Optional<MigrationPlan> plan = planMigration(sourceFolder);
      if (plan.isEmpty()) {
        return;
      }

      replayIntoFreshVsum(sourceFolder, plan.get());
    } finally {
      deleteScratchFolder();
    }
  }

  private Path scratchFolder() throws IOException {
    if (scratchFolder == null) {
      scratchFolder = Files.createTempDirectory("vitruv-migration");
    }
    return scratchFolder;
  }

  private void deleteScratchFolder() {
    if (scratchFolder == null) {
      return;
    }
    try (var paths = Files.walk(scratchFolder)) {
      paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    } catch (IOException e) {
      log.warn("Failed to delete scratch folder {}", scratchFolder, e);
    } finally {
      scratchFolder = null;
    }
  }

  private Optional<MigrationPlan> planMigration(Path sourceFolder) throws IOException {
    DetectingUserInteraction readInteraction = new DetectingUserInteraction();
    InternalVirtualModel sourceVsum = buildVsum(sourceFolder, readInteraction);
    try {
      List<EObject> roots = readRoots(sourceVsum);
      if (roots.isEmpty()) {
        log.warn(
            "No models found in {}, nothing to migrate. Persist a VSUM there first.",
            sourceFolder.toAbsolutePath());
        return Optional.empty();
      }

      Map<String, List<EObject>> rootsByNsUri = groupByNsUri(roots);
      log.info("Present models by metamodel: {}", rootsByNsUri.keySet());

      Set<Set<String>> presentNodes = resolvePresentNodes(rootsByNsUri.keySet());
      if (presentNodes.isEmpty()) {
        throw new IllegalStateException(
            "The change-propagation specifications handle none of the present models "
                + rootsByNsUri.keySet()
                + " - point the migration at a jar whose propagations match these metamodels.");
      }

      DominanceContext context = new RunnerContext(sourceVsum, presentNodes, rootsByNsUri);
      Optional<Set<String>> dominant = dominanceStrategy.selectDominant(context);
      if (dominant.isEmpty()) {
        log.warn("Dominance strategy could not pick a source of truth among {}", presentNodes);
        return Optional.empty();
      }

      Set<String> dominantNode = dominant.get();
      log.info("Dominant (source of truth) metamodel: {}", dominantNode);

      List<URI> dominantUris = dominantResourceUris(sourceVsum, dominantNode);
      List<String> targetNsUris =
          rootsByNsUri.keySet().stream()
              .filter(nsUri -> !PropagationGraph.nodeOwns(dominantNode, nsUri))
              .toList();
      if (targetNsUris.isEmpty()) {
        log.info("Only the dominant metamodel is present; no other models to re-derive.");
        return Optional.empty();
      }

      log.info(
          "Re-deriving {} from {} in place",
          targetNsUris,
          PropagationGraph.shortName(dominantNode));

      Snapshot snapshot = snapshotDominant(dominantUris);
      List<URI> modelFilesToDelete =
          allResourceUris(sourceVsum).stream()
              .filter(URI::isFile)
              .filter(uri -> !isJdkStandardLibraryResource(uri))
              .distinct()
              .toList();
      return Optional.of(new MigrationPlan(snapshot, modelFilesToDelete));
    } finally {
      sourceVsum.dispose();
    }
  }

  private void replayIntoFreshVsum(Path sourceFolder, MigrationPlan plan) throws IOException {
    deleteFiles(plan.modelFilesToDelete());
    clearVsumMetadata(sourceFolder);
    DetectingUserInteraction reDerivationInteraction =
        new DetectingUserInteraction(reDerivationInteractionFallback);
    InternalVirtualModel targetVsum = buildVsum(sourceFolder, reDerivationInteraction);
    try {
      Map<String, List<EObject>> regenerated = replayDominant(targetVsum, plan.dominant());
      log.info("In place re-derivation complete. Models now in VSUM: {}", regenerated.keySet());
      List<String> requested = reDerivationInteraction.getRequestedInteractions();
      if (requested.isEmpty()) {
        log.info("Reactions required no user interaction during re-derivation.");
      } else {
        log.info(
            "Reactions required user interaction during re-derivation (answered via fallback): {}",
            requested);
      }
    } catch (UserInteractionRequiredException e) {
      log.warn(
          "The reactions require user interaction during re-derivation. Requests seen: {}",
          reDerivationInteraction.getRequestedInteractions(),
          e);
    } finally {
      targetVsum.dispose();
    }
  }

  private Snapshot snapshotDominant(List<URI> dominantUris) {
    ResourceSet loadSet = new ResourceSetImpl();
    loadSet.getLoadOptions().put(IGNORE_FORMATTING, Boolean.TRUE);
    List<EObject> originals = new ArrayList<>();
    List<URI> originalUris = new ArrayList<>();
    for (URI dominantUri : dominantUris) {
      if (!dominantUri.isFile() || isJdkStandardLibraryResource(dominantUri)) {
        continue;
      }

      Resource resource = loadSet.getResource(dominantUri, true);
      for (EObject root : resource.getContents()) {
        if (root instanceof org.emftext.language.java.containers.Package pkg) {
          pkg.getCompilationUnits().clear();
        }

        originals.add(root);
        originalUris.add(dominantUri);
      }
    }

    return new Snapshot(new ArrayList<>(EcoreUtil.copyAll(originals)), originalUris);
  }

  private Map<String, List<EObject>> replayDominant(InternalVirtualModel vsum, Snapshot dominant) {
    registerDominantRoots(vsum, groupRootsByUri(dominant.roots(), dominant.uris()));
    return groupByNsUri(readRoots(vsum));
  }

  private void registerDominantRoots(
      InternalVirtualModel vsum, Map<URI, List<EObject>> rootsByUri) {
    boolean dominantIsJaMoPP =
        rootsByUri.values().stream()
            .flatMap(List::stream)
            .anyMatch(root -> root.eClass().getEPackage().getNsURI().startsWith(JAMOPP_NS_PREFIX));

    List<CrossResourceReference> crossReferences = detachCrossResourceReferences(rootsByUri);

    View view = emptyView(vsum);
    CommittableView committable =
        dominantIsJaMoPP ? view.withChangeRecordingTrait() : view.withChangeDerivingTrait();
    for (Map.Entry<URI, List<EObject>> entry : rootsByUri.entrySet()) {
      List<EObject> roots = entry.getValue();
      committable.registerRoot(roots.get(0), entry.getKey());
      Resource resource = roots.get(0).eResource();
      for (int i = 1; i < roots.size(); i++) {
        resource.getContents().add(roots.get(i));
      }
    }

    crossReferences.forEach(MigrationRunner::reattachCrossResourceReference);

    committable.commitChanges();
    closeQuietly(view);
  }

  private Map<String, List<EObject>> deriveInto(
      InternalVirtualModel scratchVsum, List<URI> dominantUris, Path folder) {
    Snapshot snapshot = snapshotDominant(dominantUris);
    List<URI> scratchUris =
        snapshot.uris().stream().map(uri -> persistenceUriFor(uri, folder)).toList();
    registerDominantRoots(scratchVsum, groupRootsByUri(snapshot.roots(), scratchUris));
    return groupByNsUri(readRoots(scratchVsum));
  }

  private InternalVirtualModel buildVsum(Path folder, DetectingUserInteraction interaction)
      throws IOException {
    return new VirtualModelBuilder()
        .withStorageFolder(folder)
        .withUserInteractorForResultProvider(interaction)
        .withChangePropagationSpecifications(specifications)
        .buildAndInitialize();
  }

  private Set<Set<String>> resolvePresentNodes(Collection<String> presentNsUris) {
    Set<Set<String>> nodes = new LinkedHashSet<>();
    for (String nsUri : presentNsUris) {
      Optional<Set<String>> node = graph.nodeContaining(nsUri);
      if (node.isEmpty()) {
        log.warn("Model {} is present but no registered specification handles it", nsUri);
        continue;
      }

      nodes.add(node.get());
    }

    return nodes;
  }

  record CrossResourceReference(EObject holder, EReference feature, EObject target) {}

  private record Snapshot(List<EObject> roots, List<URI> uris) {}

  private record MigrationPlan(Snapshot dominant, List<URI> modelFilesToDelete) {}

  @RequiredArgsConstructor
  private final class RunnerContext implements DominanceContext {
    private final ViewProvider sourceProvider;
    private final Set<Set<String>> presentNodes;
    private final Map<String, List<EObject>> rootsByNsUri;

    @Override
    public PropagationGraph graph() {
      return graph;
    }

    @Override
    public Set<Set<String>> presentNodes() {
      return presentNodes;
    }

    @Override
    public long reDerivationLoss(Set<String> dominantNode) {
      List<URI> dominantUris = dominantResourceUris(sourceProvider, dominantNode);
      DetectingUserInteraction interaction = new DetectingUserInteraction();
      InternalVirtualModel scratchVsum;
      Path trialFolder;
      try {
        trialFolder = scratchFolder().resolve("trial-" + (trialCounter++));
        Files.createDirectories(trialFolder);
        scratchVsum = buildVsum(trialFolder, interaction);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }

      try {
        Map<String, List<EObject>> regenerated = deriveInto(scratchVsum, dominantUris, trialFolder);
        long loss = 0;
        for (Map.Entry<String, List<EObject>> entry : rootsByNsUri.entrySet()) {
          String targetNsUri = entry.getKey();
          if (PropagationGraph.nodeOwns(dominantNode, targetNsUri)) {
            continue;
          }

          loss +=
              diff.changeCount(
                  resourcesOf(entry.getValue()),
                  resourcesOf(regenerated.getOrDefault(targetNsUri, List.of())));
        }
        return loss;
      } finally {
        scratchVsum.dispose();
      }
    }
  }
}
