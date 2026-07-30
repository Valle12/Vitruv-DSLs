package tools.vitruv.dsls.reactions.migration.migration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import tools.vitruv.change.interaction.InteractionResultProvider;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.change.propagation.ConsistencyRuleId;
import tools.vitruv.change.propagation.ConsistencyRuleTrigger;
import tools.vitruv.change.propagation.ConsistencyRuleTriggerMatcher;
import tools.vitruv.change.propagation.RuleHashRegistry;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.graph.DominancePlan;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;
import tools.vitruv.dsls.reactions.migration.graph.MigrationPlanner;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;
import tools.vitruv.dsls.reactions.migration.interaction.DetectingUserInteraction;
import tools.vitruv.dsls.reactions.migration.preservation.PreservationContext;
import tools.vitruv.dsls.reactions.migration.preservation.PreservationOutcome;
import tools.vitruv.dsls.reactions.migration.preservation.PreservationPass;
import tools.vitruv.dsls.reactions.migration.preservation.PreservationReportWriter;
import tools.vitruv.dsls.reactions.migration.preservation.PreservationReportWriter.Location;
import tools.vitruv.dsls.reactions.migration.spec.SpecificationSource;
import tools.vitruv.dsls.reactions.migration.strategy.DominanceStrategy;
import tools.vitruv.dsls.reactions.migration.vsum.ModelSnapshot;
import tools.vitruv.dsls.reactions.migration.vsum.PersistedRules;
import tools.vitruv.dsls.reactions.migration.vsum.ScratchArea;
import tools.vitruv.dsls.reactions.migration.vsum.UuidPreRegistration;
import tools.vitruv.dsls.reactions.migration.vsum.VsumBackup;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.views.CommittableView;
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

  private static Path copyPreMigrationState(Path folder, ScratchArea scratch) {
    Path copy = scratch.newFolder("pre-migration");
    try {
      VsumBackup.copyTree(folder, copy);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    return copy;
  }

  private static List<Path> copiesOf(Path copyFolder, Path originalFolder, List<URI> fileUris) {
    Path base = originalFolder.toAbsolutePath().normalize();
    List<Path> files = new ArrayList<>();
    for (URI uri : fileUris) {
      Path file = Path.of(uri.toFileString()).toAbsolutePath().normalize();
      if (!file.startsWith(base)) {
        continue;
      }

      Path copy = copyFolder.resolve(base.relativize(file).toString());
      if (Files.exists(copy)) {
        files.add(copy);
      }
    }

    return files;
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

  private static PersistedRules persistedRulesOf(Path folder) {
    PersistedRules persisted;
    try {
      persisted = PersistedRules.load(folder);
    } catch (RuntimeException unreadable) {
      throw new FallbackToFullMigration(
          "the persisted rule registry of "
              + folder
              + " cannot be read: "
              + unreadable.getMessage(),
          unreadable);
    }

    if (persisted.hashes().isEmpty()) {
      throw new FallbackToFullMigration("the VSUM has no persisted rule hashes");
    }

    return persisted;
  }

  private static List<ConsistencyRuleTriggerMatcher> matchersFor(
      SortedSet<ConsistencyRuleId> dirtyIds,
      PersistedRules persisted,
      RuleHashRegistry currentRules) {
    Map<ConsistencyRuleId, ConsistencyRuleTrigger> currentTriggers = currentRules.getRuleTriggers();
    List<ConsistencyRuleTriggerMatcher> matchers = new ArrayList<>();
    for (ConsistencyRuleId dirtyId : dirtyIds) {
      ConsistencyRuleTrigger trigger =
          currentTriggers.getOrDefault(dirtyId, persisted.triggers().get(dirtyId));
      if (trigger == null) {
        throw new FallbackToFullMigration(
            "no trigger descriptor is available for rule " + dirtyId.value());
      }

      matchers.add(
          ConsistencyRuleTriggerMatcher.of(trigger)
              .orElseThrow(
                  () ->
                      new FallbackToFullMigration(
                          "the trigger of rule "
                              + dirtyId.value()
                              + " references a metaclass that is not registered: "
                              + trigger.serialize())));
    }

    return matchers;
  }

  private static List<EObject> affectedSourceElements(
      CommittableView committable,
      List<ConsistencyRuleTriggerMatcher> matchers,
      Set<URI> sourceUris,
      NotRepropagated notRepropagated) {
    List<EObject> sourceRoots =
        committable.getRootObjects().stream()
            .filter(root -> isOwnedByResourceIn(root, sourceUris))
            .toList();
    List<EObject> matched =
        AffectedElements.find(sourceRoots, matchers, notRepropagated.asExclusion());
    return AffectedElements.withSourceReferrers(
        matched, sourceRoots, notRepropagated.asExclusion());
  }

  private static boolean isOwnedByResourceIn(EObject element, Set<URI> resourceUris) {
    Resource resource = element.eResource();
    return resource != null
        && resource.getURI() != null
        && resourceUris.contains(resource.getURI());
  }

  private static void requireNoAffectedRoots(List<EObject> affected) {
    for (EObject element : affected) {
      if (element.eContainer() == null) {
        throw new FallbackToFullMigration(
            "the affected element " + EcoreUtil.getURI(element) + " is a resource root");
      }
    }
  }

  private static void logNothingToDerive(String run) {
    log.info("Every present metamodel is a source of truth; the {} has nothing to re-derive.", run);
  }

  private static Predicate<EObject> derivedRootPredicate(DominancePlan plan) {
    Map<String, Boolean> owned = new HashMap<>();
    return root ->
        owned.computeIfAbsent(
            root.eClass().getEPackage().getNsURI(),
            nsUri -> plan.derived().stream().anyMatch(node -> node.owns(nsUri)));
  }

  private static PreservationOutcome recordPreservation(
      Path folder, Path preMigrationFolder, PreservationOutcome preservation) {
    PreservationOutcome outcome = keepRecoveryState(folder, preMigrationFolder, preservation);
    if (outcome.attempted()) {
      PreservationReportWriter.write(folder, outcome, Location.VSUM_FOLDER);
    }

    return outcome;
  }

  private static PreservationOutcome keepRecoveryState(
      Path folder, Path preMigrationFolder, PreservationOutcome preservation) {
    if (preservation.lost().isEmpty()) {
      return preservation;
    }

    Path recoveryFolder =
        folder.resolveSibling(
            "%s-premigration-%d".formatted(folder.getFileName(), System.currentTimeMillis()));
    try {
      VsumBackup.copyTree(preMigrationFolder, recoveryFolder);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    PreservationOutcome recoverable = preservation.recoverableFrom(recoveryFolder);
    log.warn(
        "{} item(s) of the old derived models could not be kept; recover them from {} (see {})",
        preservation.lost().size(),
        recoveryFolder.toAbsolutePath(),
        PreservationReportWriter.write(recoveryFolder, recoverable, Location.RECOVERY_FOLDER)
            .getFileName());
    return recoverable;
  }

  public MigrationReport run(Path vsumFolder) {
    PhaseTimer timer = new PhaseTimer();
    Vsums.normalizeModelRegistry(vsumFolder);
    if (settings.mode() == MigrationMode.FULL) {
      return runFull(vsumFolder, SelectiveOutcome.notRequested(), timer);
    }

    return runSelective(vsumFolder, timer);
  }

  private MigrationReport runFull(Path vsumFolder, SelectiveOutcome selective, PhaseTimer timer) {
    try (ScratchArea scratch = new ScratchArea()) {
      Optional<PreparedMigration> prepared =
          timer.time("prepare", () -> prepareMigration(vsumFolder, scratch));
      return prepared
          .map(
              preparedMigration ->
                  reDeriveInPlace(vsumFolder, preparedMigration, scratch, selective, timer))
          .orElseGet(() -> MigrationReport.nothingToDo(selective, timer.statistics()));
    }
  }

  private MigrationReport runSelective(Path vsumFolder, PhaseTimer timer) {
    try {
      return attemptSelective(vsumFolder, timer);
    } catch (FallbackToFullMigration fallback) {
      log.warn(
          "Selective migration not possible ({}), falling back to a full migration.",
          fallback.getMessage());
      return runFull(
          vsumFolder, SelectiveOutcome.fellBack(settings.mode(), fallback.getMessage()), timer);
    }
  }

  private MigrationReport attemptSelective(Path folder, PhaseTimer timer) {
    DirtyRules dirty = timer.time("rule-diff", () -> dirtyRulesOf(folder));
    if (dirty.isEmpty()) {
      log.info(
          "The persisted rule registry matches the new specifications in mode {};"
              + " nothing to repropagate.",
          settings.mode());
      return MigrationReport.nothingToDo(
          SelectiveOutcome.clean(settings.mode()), timer.statistics());
    }

    return repropagateAffectedElements(folder, dirty, timer);
  }

  private DirtyRules dirtyRulesOf(Path folder) {
    PersistedRules persisted = persistedRulesOf(folder);
    RuleHashRegistry currentRules = currentRulesOfNewSpecifications();
    RuleHashDiff diff = RuleHashDiff.between(persisted.hashes(), currentRules.getRuleHashes());
    SortedSet<ConsistencyRuleId> dirtyIds = diff.dirtyIds(settings.mode());
    if (dirtyIds.isEmpty()) {
      return new DirtyRules(dirtyIds, List.of());
    }

    log.info(
        "Dirty rules in mode {}: {}",
        settings.mode(),
        dirtyIds.stream().map(ConsistencyRuleId::value).toList());
    return new DirtyRules(dirtyIds, matchersFor(dirtyIds, persisted, currentRules));
  }

  private RuleHashRegistry currentRulesOfNewSpecifications() {
    RuleHashRegistry registry = new RuleHashRegistry();
    for (ChangePropagationSpecification specification : specifications.createSpecifications()) {
      registry.putConsistencyRules(
          specification.getConsistencyRuleHashes(), specification.getConsistencyRuleTriggers());
    }

    if (registry.isEmpty()) {
      throw new FallbackToFullMigration("the new specifications expose no rule hashes");
    }

    return registry;
  }

  private MigrationReport repropagateAffectedElements(
      Path folder, DirtyRules dirty, PhaseTimer timer) {
    try (ScratchArea scratch = new ScratchArea()) {
      DetectingUserInteraction interaction = new DetectingUserInteraction(interactionFallback);
      InternalVirtualModel vsum =
          Vsums.build(folder, specifications.createSpecifications(), interaction);
      try {
        List<EObject> roots = Vsums.readRoots(vsum);
        if (roots.isEmpty()) {
          log.warn("No models found in {}, nothing to migrate.", folder.toAbsolutePath());
          return MigrationReport.nothingToDo(
              SelectiveOutcome.clean(settings.mode()), timer.statistics());
        }

        Map<String, List<EObject>> rootsByNsUri = ModelGroups.byNsUri(roots);
        Set<MetamodelNode> presentNodes = presentNodes(rootsByNsUri.keySet());
        if (presentNodes.isEmpty()) {
          throw new FallbackToFullMigration(
              "no registered specification handles the present models " + rootsByNsUri.keySet());
        }

        TrialMigration trialMigration =
            new TrialMigration(specifications, adapters, interactionFallback, scratch, folder);
        DominancePlan plan =
            new MigrationPlanner(graph, strategy)
                .plan(
                    new RunnerContext(
                        graph, vsum, presentNodes, rootsByNsUri, adapters, trialMigration, folder));
        if (plan.derived().isEmpty()) {
          logNothingToDerive("selective run");
          return MigrationReport.nothingToDo(
              SelectiveOutcome.clean(settings.mode()), timer.statistics());
        }

        Set<URI> sourceUris =
            Set.copyOf(classifyResources(vsum, plan, presentNodes, folder).snapshotUris());
        Repropagation repropagation =
            repropagateInView(vsum, dirty.matchers(), sourceUris, folder, scratch, timer);
        if (!repropagation.happened()) {
          return selectiveReport(
              plan, interaction, dirty.ids(), 0, PreservationOutcome.skipped(), timer);
        }

        PreservationOutcome preservation =
            preserveUserContentIfEnabled(
                folder, vsum, repropagation.preMigrationFolder(), plan, timer);
        refreshSerializedForms(vsum);
        return selectiveReport(
            plan,
            interaction,
            dirty.ids(),
            repropagation.affectedCount(),
            recordPreservation(folder, repropagation.preMigrationFolder(), preservation),
            timer);
      } finally {
        vsum.dispose();
      }
    }
  }

  private Repropagation repropagateInView(
      InternalVirtualModel vsum,
      List<ConsistencyRuleTriggerMatcher> matchers,
      Set<URI> sourceUris,
      Path folder,
      ScratchArea scratch,
      PhaseTimer timer) {
    dropResolutionCaches(vsum);
    NotRepropagated notRepropagated = NotRepropagated.of(vsum, sourceUris, folder, adapters);
    try (View view = Vsums.openViewOfAll(vsum, "selective-repropagation")) {
      CommittableView committable = view.withChangeRecordingTrait();
      List<EObject> affected =
          timer.time(
              "selection",
              () -> affectedSourceElements(committable, matchers, sourceUris, notRepropagated));
      requireNoAffectedRoots(affected);
      if (affected.isEmpty()) {
        log.info(
            "The dirty rules match no source elements; the persisted registry is left unchanged"
                + " and a rerun will re-detect them.");
        return Repropagation.none();
      }

      log.info("Repropagating {} affected source element(s) in place", affected.size());
      Path preMigrationFolder = snapshotForPreservation(folder, scratch, timer);
      int lateRegistrations =
          timer.time(
              "preregister",
              () -> UuidPreRegistration.registerAllLiveElements(vsum, adapters, folder));
      if (lateRegistrations > 0) {
        log.info(
            "Registered {} model element(s) the persisted uuid registry did not cover",
            lateRegistrations);
      }

      SelectiveRepropagation.execute(
          committable,
          affected,
          element -> isOwnedByResourceIn(element, sourceUris),
          adapters,
          folder,
          timer);
      return new Repropagation(affected.size(), preMigrationFolder);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Selective repropagation failed", e);
    }
  }

  private void dropResolutionCaches(InternalVirtualModel vsum) {
    for (Resource resource : List.copyOf(vsum.getViewSourceModels())) {
      for (EObject root : List.copyOf(resource.getContents())) {
        adapters.adapterFor(root).dropResolutionCaches(root);
      }
    }
  }

  private void refreshSerializedForms(InternalVirtualModel vsum) {
    List<String> rewritten = new ArrayList<>();
    for (Resource resource : List.copyOf(vsum.getViewSourceModels())) {
      if (adapters.adapterFor(resource).refreshSerializedForm(resource)) {
        rewritten.add(resource.getURI().lastSegment());
      }
    }

    if (!rewritten.isEmpty()) {
      log.info(
          "Rewrote {} file(s) the migration changed only indirectly: {}",
          rewritten.size(),
          rewritten);
    }
  }

  private Path snapshotForPreservation(Path folder, ScratchArea scratch, PhaseTimer timer) {
    if (!settings.preservation().analyses()) {
      return null;
    }

    return timer.time("snapshot", () -> copyPreMigrationState(folder, scratch));
  }

  private MigrationReport selectiveReport(
      DominancePlan plan,
      DetectingUserInteraction interaction,
      SortedSet<ConsistencyRuleId> dirtyIds,
      int affectedCount,
      PreservationOutcome preservation,
      PhaseTimer timer) {
    return new MigrationReport(
        affectedCount > 0,
        plan.sources(),
        plan.derived(),
        interaction.getRequestedInteractions(),
        SourceUpdateOutcome.skipped(),
        SelectiveOutcome.applied(settings.mode(), dirtyIds.size(), affectedCount),
        preservation,
        timer.statistics());
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
                      graph,
                      sourceVsum,
                      presentNodes,
                      rootsByNsUri,
                      adapters,
                      trialMigration,
                      folder));
      log.info("Sources of truth (dominant first): {}", shortNames(plan.sources()));
      if (plan.derived().isEmpty()) {
        logNothingToDerive("full migration");
        return Optional.empty();
      }

      log.info("Re-deriving {} from the sources in place", shortNames(plan.derived()));
      ResourceClassification classification =
          classifyResources(sourceVsum, plan, presentNodes, folder);
      ModelSnapshot snapshot = ModelSnapshot.of(classification.snapshotUris(), adapters, folder);
      Path preMigrationFolder = copyPreMigrationState(folder, scratch);
      return Optional.of(
          new PreparedMigration(
              plan,
              snapshot,
              preMigrationFolder,
              copiesOf(preMigrationFolder, folder, classification.derivedFileUris()),
              classification.allModelFileUris()));
    } finally {
      sourceVsum.dispose();
    }
  }

  private MigrationReport reDeriveInPlace(
      Path folder,
      PreparedMigration prepared,
      ScratchArea scratch,
      SelectiveOutcome selective,
      PhaseTimer timer) {
    deleteModelFiles(prepared.modelFilesToDelete());
    clearVsumMetadata(folder);
    DetectingUserInteraction reDerivationInteraction =
        new DetectingUserInteraction(interactionFallback);
    InternalVirtualModel targetVsum =
        Vsums.build(folder, specifications.createSpecifications(), reDerivationInteraction);
    SourceUpdateOutcome sourceUpdate;
    PreservationOutcome preservation;
    try {
      timer.time(
          "re-derive",
          () -> new Replayer(adapters).replayInto(targetVsum, prepared.sourceSnapshot()));
      log.info("In-place re-derivation complete.");
      sourceUpdate =
          timer.time(
              "source-update", () -> updateSourcesIfEnabled(folder, targetVsum, prepared, scratch));
      preservation =
          preserveUserContentIfEnabled(
              folder, targetVsum, prepared.preMigrationFolder(), prepared.plan(), timer);
      refreshSerializedForms(targetVsum);
      logRequestedInteractions(reDerivationInteraction);
    } finally {
      targetVsum.dispose();
    }

    return new MigrationReport(
        true,
        prepared.plan().sources(),
        prepared.plan().derived(),
        reDerivationInteraction.getRequestedInteractions(),
        sourceUpdate,
        selective,
        recordPreservation(folder, prepared.preMigrationFolder(), preservation),
        timer.statistics());
  }

  private PreservationOutcome preserveUserContentIfEnabled(
      Path folder,
      InternalVirtualModel targetVsum,
      Path preMigrationFolder,
      DominancePlan plan,
      PhaseTimer timer) {
    if (!settings.preservation().analyses() || preMigrationFolder == null) {
      return PreservationOutcome.skipped();
    }

    return timer.time(
        "preserve",
        () ->
            PreservationPass.run(
                new PreservationContext(
                    targetVsum,
                    folder,
                    preMigrationFolder,
                    adapters,
                    derivedRootPredicate(plan),
                    settings.preservation(),
                    settings.askOnAmbiguity() ? interactionFallback : null)));
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
      InternalVirtualModel vsum,
      DominancePlan plan,
      Set<MetamodelNode> presentNodes,
      Path vsumFolder) {
    Set<URI> snapshotUris = new LinkedHashSet<>();
    Set<URI> derivedUris = new LinkedHashSet<>();
    Set<URI> allFileUris = new LinkedHashSet<>();
    try (View view = Vsums.openViewOfAll(vsum, "classify-resources")) {
      for (EObject root : view.getRootObjects()) {
        Resource resource = root.eResource();
        if (resource == null || !adapters.isMigratedModel(resource.getURI(), vsumFolder)) {
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

  private record DirtyRules(
      SortedSet<ConsistencyRuleId> ids, List<ConsistencyRuleTriggerMatcher> matchers) {
    boolean isEmpty() {
      return ids.isEmpty();
    }
  }
}
