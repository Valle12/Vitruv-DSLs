package tools.vitruv.dsls.reactions.migration.migration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import tools.vitruv.change.interaction.InteractionResultProvider;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.graph.DominancePlan;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;
import tools.vitruv.dsls.reactions.migration.graph.MigrationPlanner;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;
import tools.vitruv.dsls.reactions.migration.interaction.DetectingUserInteraction;
import tools.vitruv.dsls.reactions.migration.spec.SpecificationSource;
import tools.vitruv.dsls.reactions.migration.strategy.DominanceStrategy;
import tools.vitruv.dsls.reactions.migration.vsum.ModelSnapshot;
import tools.vitruv.dsls.reactions.migration.vsum.ScratchArea;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.helper.VsumFileSystemLayout;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

@Slf4j
@RequiredArgsConstructor
public class MigrationRunner {
  private final SpecificationSource specifications;
  private final PropagationGraph graph;
  private final DominanceStrategy strategy;
  private final InteractionResultProvider interactionFallback;
  private final AdapterRegistry adapters;
  private final MigrationSettings settings;

  private static MetamodelNode owningNode(Set<MetamodelNode> presentNodes, EObject root) {
    String nsUri = root.eClass().getEPackage().getNsURI();
    return presentNodes.stream().filter(node -> node.owns(nsUri)).findFirst().orElse(null);
  }

  private static Path copyDerivedOriginals(
      Path folder, List<URI> derivedFileUris, ScratchArea scratch) {
    Path backupFolder = scratch.newFolder("derived-originals");
    Path base = folder.toAbsolutePath().normalize();
    try {
      for (URI uri : derivedFileUris) {
        Path file = Path.of(uri.toFileString()).toAbsolutePath().normalize();
        if (!file.startsWith(base) || !Files.exists(file)) {
          continue;
        }

        Path destination = backupFolder.resolve(base.relativize(file).toString());
        Files.createDirectories(destination.getParent());
        Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    return backupFolder;
  }

  private static void deleteModelFiles(List<URI> fileUris) {
    try {
      for (URI uri : fileUris) {
        if (Files.deleteIfExists(Path.of(uri.toFileString()))) {
          log.info("Removed old model file {} before re-derivation", uri.lastSegment());
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void clearVsumMetadata(Path folder) {
    VsumFileSystemLayout layout = new VsumFileSystemLayout(folder);
    try {
      layout.prepare();
      Files.deleteIfExists(Path.of(layout.getCorrespondencesURI().toFileString()));
      Files.deleteIfExists(Path.of(layout.getUuidsURI().toFileString()));
      Files.deleteIfExists(layout.getModelsNamesFilesPath());
      log.info("Cleared stale VSUM metadata before re-derivation");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void logRequestedInteractions(DetectingUserInteraction interaction) {
    List<String> requested = interaction.getRequestedInteractions();
    if (requested.isEmpty()) {
      log.info("Reactions required no user interaction during re-derivation.");
    } else {
      log.info(
          "Reactions required user interaction during re-derivation (answered via fallback): {}",
          requested);
    }
  }

  private static List<String> shortNames(List<MetamodelNode> nodes) {
    return nodes.stream().map(MetamodelNode::shortName).toList();
  }

  public MigrationReport run(Path vsumFolder) {
    try (ScratchArea scratch = new ScratchArea()) {
      Optional<PreparedMigration> prepared = prepareMigration(vsumFolder, scratch);
      return prepared
          .map(preparedMigration -> reDeriveInPlace(vsumFolder, preparedMigration, scratch))
          .orElseGet(MigrationReport::nothingToDo);
    }
  }

  private Optional<PreparedMigration> prepareMigration(Path folder, ScratchArea scratch) {
    InternalVirtualModel sourceVsum =
        Vsums.build(folder, specifications.createSpecifications(), new DetectingUserInteraction());
    try {
      List<EObject> roots = Vsums.readRoots(sourceVsum);
      if (roots.isEmpty()) {
        log.warn(
            "No models found in {}, nothing to migrate. Persist a VSUM there first.",
            folder.toAbsolutePath());
        return Optional.empty();
      }

      Map<String, List<EObject>> rootsByNsUri = ModelGroups.byNsUri(roots);
      log.info("Present models by metamodel: {}", rootsByNsUri.keySet());
      Set<MetamodelNode> presentNodes = presentNodes(rootsByNsUri.keySet());
      if (presentNodes.isEmpty()) {
        throw new IllegalStateException(
            "The change-propagation specifications handle none of the present models "
                + rootsByNsUri.keySet()
                + " - point the migration at a jar whose propagations match these metamodels.");
      }

      TrialMigration trialMigration =
          new TrialMigration(specifications, adapters, interactionFallback, scratch, folder);
      DominancePlan plan =
          new MigrationPlanner(graph, strategy)
              .plan(
                  new RunnerContext(
                      graph, sourceVsum, presentNodes, rootsByNsUri, adapters, trialMigration));
      log.info("Sources of truth (dominant first): {}", shortNames(plan.sources()));
      if (plan.derived().isEmpty()) {
        log.info("Every present metamodel is a source of truth; there is nothing to re-derive.");
        return Optional.empty();
      }

      log.info("Re-deriving {} from the sources in place", shortNames(plan.derived()));
      ResourceClassification classification = classifyResources(sourceVsum, plan, presentNodes);
      ModelSnapshot snapshot = ModelSnapshot.of(classification.snapshotUris(), adapters);
      Path derivedOriginals =
          copyDerivedOriginals(folder, classification.derivedFileUris(), scratch);
      return Optional.of(
          new PreparedMigration(
              plan, snapshot, derivedOriginals, classification.allModelFileUris()));
    } finally {
      sourceVsum.dispose();
    }
  }

  private MigrationReport reDeriveInPlace(
      Path folder, PreparedMigration prepared, ScratchArea scratch) {
    deleteModelFiles(prepared.modelFilesToDelete());
    clearVsumMetadata(folder);
    DetectingUserInteraction reDerivationInteraction =
        new DetectingUserInteraction(interactionFallback);
    InternalVirtualModel targetVsum =
        Vsums.build(folder, specifications.createSpecifications(), reDerivationInteraction);
    SourceUpdateOutcome sourceUpdate;
    try {
      new Replayer(adapters).replayInto(targetVsum, prepared.sourceSnapshot());
      log.info("In-place re-derivation complete.");
      sourceUpdate = updateSourcesIfEnabled(folder, targetVsum, prepared, scratch);
      logRequestedInteractions(reDerivationInteraction);
    } finally {
      targetVsum.dispose();
    }

    return new MigrationReport(
        true,
        prepared.plan().sources(),
        prepared.plan().derived(),
        reDerivationInteraction.getRequestedInteractions(),
        sourceUpdate);
  }

  private SourceUpdateOutcome updateSourcesIfEnabled(
      Path folder,
      InternalVirtualModel targetVsum,
      PreparedMigration prepared,
      ScratchArea scratch) {
    if (!settings.updateSources()) {
      log.info("Source update disabled; keeping the forward re-derivation as-is.");
      return SourceUpdateOutcome.skipped();
    }

    return new SourceUpdater(
            specifications, adapters, interactionFallback, settings.maxSourceUpdateRounds())
        .update(folder, targetVsum, prepared, scratch);
  }

  private Set<MetamodelNode> presentNodes(Collection<String> presentNsUris) {
    Set<MetamodelNode> nodes = new LinkedHashSet<>();
    for (String nsUri : presentNsUris) {
      graph
          .nodeContaining(nsUri)
          .ifPresentOrElse(
              nodes::add,
              () ->
                  log.warn(
                      "Model {} is present but no registered specification handles it", nsUri));
    }

    return nodes;
  }

  private ResourceClassification classifyResources(
      InternalVirtualModel vsum, DominancePlan plan, Set<MetamodelNode> presentNodes) {
    Set<URI> snapshotUris = new LinkedHashSet<>();
    Set<URI> derivedUris = new LinkedHashSet<>();
    Set<URI> allFileUris = new LinkedHashSet<>();
    try (View view = Vsums.openViewOfAll(vsum, "classify-resources")) {
      for (EObject root : view.getRootObjects()) {
        Resource resource = root.eResource();
        if (resource == null
            || resource.getURI() == null
            || !resource.getURI().isFile()
            || adapters.isPlatformLibraryResource(resource.getURI())) {
          continue;
        }

        URI uri = resource.getURI();
        allFileUris.add(uri);
        MetamodelNode owner = owningNode(presentNodes, root);
        if (owner == null || plan.sources().contains(owner)) {
          snapshotUris.add(uri);
        } else {
          derivedUris.add(uri);
        }
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to classify the VSUM's resources", e);
    }

    snapshotUris.removeAll(derivedUris);
    return new ResourceClassification(
        List.copyOf(snapshotUris), List.copyOf(derivedUris), List.copyOf(allFileUris));
  }
}
