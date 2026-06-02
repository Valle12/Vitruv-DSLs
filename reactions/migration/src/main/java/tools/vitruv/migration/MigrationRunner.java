package tools.vitruv.migration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.ViewProvider;
import tools.vitruv.framework.views.ViewSelector;
import tools.vitruv.framework.views.ViewType;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

/**
 * Orchestrates a single migration run:
 *
 * <ol>
 *   <li>load the persisted VSUM from a folder and read the present models;
 *   <li>choose a dominant ("source of truth") metamodel via the {@link DominanceStrategy};
 *   <li>re-derive the remaining models from the dominant one in a fresh scratch VSUM that uses the
 *       (possibly changed) reactions, recording the derived state as a change so the reactions
 *       react to it;
 *   <li>report whether any reaction required user interaction (via {@link
 *       DetectingUserInteraction}) and which target models were regenerated.
 * </ol>
 *
 * <p>Re-derivation runs in a <em>fresh</em> VSUM rather than in-place, because deleting the derived
 * side inside the loaded VSUM would propagate the deletion across the existing correspondences and
 * remove the source side too.
 */
@Slf4j
public final class MigrationRunner {
  private static final String IGNORE_FORMATTING = "DISABLE_LAYOUT_INFORMATION_RECORDING";
  private final List<ChangePropagationSpecification> specifications;
  private final PropagationGraph graph;
  private final DominanceStrategy dominanceStrategy;
  private final Path scratchFolder;
  private final StateBasedDiff diff = new StateBasedDiff();
  private int trialCounter = 0;

  public MigrationRunner(
      List<ChangePropagationSpecification> specifications,
      PropagationGraph graph,
      DominanceStrategy dominanceStrategy,
      Path scratchFolder) {
    this.specifications = List.copyOf(specifications);
    this.graph = graph;
    this.dominanceStrategy = dominanceStrategy;
    this.scratchFolder = scratchFolder;
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

  private static Map<String, List<EObject>> groupByNsUri(List<EObject> roots) {
    return roots.stream()
        .collect(
            Collectors.groupingBy(
                root -> root.eClass().getEPackage().getNsURI(),
                LinkedHashMap::new,
                Collectors.toList()));
  }

  private static Collection<Resource> resourcesOf(List<EObject> roots) {
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

  private static URI persistenceUriFor(URI sourceUri, Path folder) {
    String fileName =
        sourceUri != null && sourceUri.lastSegment() != null
            ? sourceUri.lastSegment()
            : "model.model";
    return URI.createFileURI(folder.resolve(fileName).toString());
  }

  public void run(Path sourceFolder) throws IOException {
    DetectingUserInteraction sourceInteraction = new DetectingUserInteraction();
    InternalVirtualModel sourceVsum = buildVsum(sourceFolder, sourceInteraction);
    try {
      List<EObject> roots = readRoots(sourceVsum);
      if (roots.isEmpty()) {
        log.warn(
            "No models found in {} - nothing to migrate. Persist a VSUM there first.",
            sourceFolder.toAbsolutePath());
        return;
      }

      Map<String, List<EObject>> rootsByNsUri = groupByNsUri(roots);
      log.info("Present models by metamodel: {}", rootsByNsUri.keySet());

      Set<Set<String>> presentNodes = resolvePresentNodes(rootsByNsUri.keySet());
      DominanceContext context = new RunnerContext(sourceVsum, presentNodes, rootsByNsUri);
      Optional<Set<String>> dominant = dominanceStrategy.selectDominant(context);
      if (dominant.isEmpty()) {
        log.warn("Dominance strategy could not pick a source of truth among {}", presentNodes);
        return;
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
        return;
      }

      log.info("Re-deriving {} from {}", targetNsUris, PropagationGraph.shortName(dominantNode));

      finalReDerive(dominantUris);
    } finally {
      sourceVsum.dispose();
    }
  }

  private void finalReDerive(List<URI> dominantUris) throws IOException {
    Path resultFolder = scratchFolder.resolve("result");
    Files.createDirectories(resultFolder);
    DetectingUserInteraction interaction = new DetectingUserInteraction();
    InternalVirtualModel scratchVsum = buildVsum(resultFolder, interaction);
    try {
      Map<String, List<EObject>> regenerated = deriveInto(scratchVsum, dominantUris, resultFolder);
      log.info("Re-derivation complete. Models now in scratch VSUM: {}", regenerated.keySet());
      log.info(
          "Reactions did NOT require any user interaction during re-derivation: {}",
          interaction.getRequestedInteractions().isEmpty());
    } catch (DetectingUserInteraction.UserInteractionRequiredException e) {
      log.warn(
          "The reactions require user interaction during re-derivation. Requests seen: {}",
          interaction.getRequestedInteractions(),
          e);
    } finally {
      scratchVsum.dispose();
    }
  }

  private Map<String, List<EObject>> deriveInto(
      InternalVirtualModel scratchVsum, List<URI> dominantUris, Path folder) {
    ResourceSet loadSet = new ResourceSetImpl();
    loadSet.getLoadOptions().put(IGNORE_FORMATTING, Boolean.TRUE);
    List<EObject> originals = new ArrayList<>();
    List<URI> originalUris = new ArrayList<>();
    for (URI dominantUri : dominantUris) {
      Resource resource = loadSet.getResource(dominantUri, true);
      for (EObject root : resource.getContents()) {
        originals.add(root);
        originalUris.add(dominantUri);
      }
    }

    List<EObject> copies = new ArrayList<>(EcoreUtil.copyAll(originals));

    View view = viewOfAll(scratchVsum, "migration-source");
    CommittableView committable = view.withChangeRecordingTrait();
    for (int i = 0; i < copies.size(); i++) {
      committable.registerRoot(copies.get(i), persistenceUriFor(originalUris.get(i), folder));
    }

    committable.commitChanges();
    closeQuietly(view);
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
        log.warn(
            "Model {} is present but no registered specification handles it; ignoring.", nsUri);
        continue;
      }

      nodes.add(node.get());
    }

    return nodes;
  }

  private final class RunnerContext implements DominanceContext {
    private final ViewProvider sourceProvider;
    private final Set<Set<String>> presentNodes;
    private final Map<String, List<EObject>> rootsByNsUri;

    RunnerContext(
        ViewProvider sourceProvider,
        Set<Set<String>> presentNodes,
        Map<String, List<EObject>> rootsByNsUri) {
      this.sourceProvider = sourceProvider;
      this.presentNodes = presentNodes;
      this.rootsByNsUri = rootsByNsUri;
    }

    @Override
    public PropagationGraph graph() {
      return graph;
    }

    @Override
    public Collection<Set<String>> presentNodes() {
      return presentNodes;
    }

    @Override
    public long reDerivationLoss(Set<String> dominantNode) {
      List<URI> dominantUris = dominantResourceUris(sourceProvider, dominantNode);
      Path trialFolder = scratchFolder.resolve("trial-" + (trialCounter++));
      DetectingUserInteraction interaction = new DetectingUserInteraction();
      InternalVirtualModel scratchVsum;
      try {
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
