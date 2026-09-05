package tools.vitruv.dsls.reactions.migration.migration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
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
import tools.vitruv.dsls.reactions.migration.vsum.VsumRelocation;
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

  private static void adoptTrial(Path trialFolder, Path folder) {
    VsumRelocation.copy(trialFolder, folder);
    Vsums.normalizeModelRegistry(folder);
    log.info(
        "Adopted the trial migration in {} as the result instead of re-deriving again",
        trialFolder.getFileName());
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

  private static List<DirtyMatcher> matchersFor(
      SortedSet<ConsistencyRuleId> dirtyIds,
      PersistedRules persisted,
      RuleHashRegistry currentRules) {
    Map<ConsistencyRuleId, ConsistencyRuleTrigger> currentTriggers = currentRules.getRuleTriggers();
    List<DirtyMatcher> matchers = new ArrayList<>();
    for (ConsistencyRuleId dirtyId : dirtyIds) {
      ConsistencyRuleTrigger trigger =
          currentTriggers.getOrDefault(dirtyId, persisted.triggers().get(dirtyId));
      if (trigger == null) {
        throw new FallbackToFullMigration(
            "no trigger descriptor is available for rule " + dirtyId.value());
      }

      matchers.add(
          new DirtyMatcher(
              dirtyId,
              ConsistencyRuleTriggerMatcher.of(trigger)
                  .orElseThrow(
                      () ->
                          new FallbackToFullMigration(
                              "the trigger of rule "
                                  + dirtyId.value()
                                  + " references a metaclass that is not registered: "
                                  + trigger.serialize()))));
    }

    return matchers;
  }

  private static AffectedElements.Selection affectedSourceElements(
      CommittableView committable,
      List<DirtyMatcher> matchers,
      Set<URI> sourceUris,
      NotRepropagated notRepropagated) {
    List<EObject> sourceRoots =
        committable.getRootObjects().stream()
            .filter(root -> isOwnedByResourceIn(root, sourceUris))
            .toList();
    return AffectedElements.select(sourceRoots, matchers, notRepropagated.asExclusion());
  }

  private static List<SelectiveOutcome.AffectedElement> describe(
      List<AffectedElements.Selected> selected) {
    return selected.stream()
        .map(
            each ->
                new SelectiveOutcome.AffectedElement(
                    NotRepropagated.keyOf(each.element()),
                    NotRepropagated.metaclassOf(each.element()),
                    each.matchedBy().stream().map(ConsistencyRuleId::value).toList(),
                    Optional.ofNullable(each.refersTo()).map(NotRepropagated::keyOf)))
        .toList();
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

  private static LoadedSource loadSource(InternalVirtualModel vsum) {
    try {
      return new LoadedSource(vsum, ModelGroups.byNsUri(Vsums.liveRoots(vsum)));
    } catch (RuntimeException e) {
      vsum.dispose();
      throw e;
    }
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
      Optional<PreparedMigration> prepared = prepareMigration(vsumFolder, scratch, timer);
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
          SelectiveOutcome.clean(settings.mode(), dirty.report(Map.of())), timer.statistics());
    }

    return repropagateAffectedElements(folder, dirty, timer);
  }

  private DirtyRules dirtyRulesOf(Path folder) {
    PersistedRules persisted = persistedRulesOf(folder);
    CurrentRules current = currentRulesOfNewSpecifications();
    RuleHashRegistry currentRules = current.registry();
    RuleHashDiff diff = RuleHashDiff.between(persisted.hashes(), currentRules.getRuleHashes());
    SortedSet<ConsistencyRuleId> dirtyIds = diff.dirtyIds(settings.mode());
    int persistedCount = persisted.hashes().size();
    int currentCount = currentRules.getRuleHashes().size();
    if (dirtyIds.isEmpty()) {
      return new DirtyRules(
          diff, dirtyIds, List.of(), persistedCount, currentCount, current.specifications());
    }

    log.info(
        "Dirty rules in mode {}: {}",
        settings.mode(),
        dirtyIds.stream().map(ConsistencyRuleId::value).toList());
    return new DirtyRules(
        diff,
        dirtyIds,
        matchersFor(dirtyIds, persisted, currentRules),
        persistedCount,
        currentCount,
        current.specifications());
  }

  private CurrentRules currentRulesOfNewSpecifications() {
    RuleHashRegistry registry = new RuleHashRegistry();
    List<ChangePropagationSpecification> instances = specifications.createSpecifications();
    for (ChangePropagationSpecification specification : instances) {
      registry.putConsistencyRules(
          specification.getConsistencyRuleHashes(), specification.getConsistencyRuleTriggers());
    }

    if (registry.isEmpty()) {
      throw new FallbackToFullMigration("the new specifications expose no rule hashes");
    }

    return new CurrentRules(registry, instances);
  }

  private MigrationReport repropagateAffectedElements(
      Path folder, DirtyRules dirty, PhaseTimer timer) {
    try (ScratchArea scratch = new ScratchArea()) {
      DetectingUserInteraction interaction = new DetectingUserInteraction(interactionFallback);
      InternalVirtualModel vsum =
          timer.time("load", () -> Vsums.build(folder, dirty.specifications(), interaction));
      try {
        List<EObject> roots = timer.time("roots", () -> Vsums.liveRoots(vsum));
        if (roots.isEmpty()) {
          log.warn("No models found in {}, nothing to migrate.", folder.toAbsolutePath());
          return MigrationReport.nothingToDo(
              SelectiveOutcome.clean(settings.mode(), dirty.report(Map.of())), timer.statistics());
        }

        Map<String, List<EObject>> rootsByNsUri = ModelGroups.byNsUri(roots);
        Set<MetamodelNode> presentNodes = presentNodes(rootsByNsUri.keySet());
        if (presentNodes.isEmpty()) {
          throw new FallbackToFullMigration(
              "no registered specification handles the present models " + rootsByNsUri.keySet());
        }

        RunnerContext context =
            new RunnerContext(
                graph,
                vsum,
                presentNodes,
                adapters,
                new TrialMigration(specifications, adapters, interactionFallback, scratch, folder),
                folder);
        DominancePlan plan =
            timer.time("dominance", () -> new MigrationPlanner(graph, strategy).plan(context));
        if (plan.derived().isEmpty()) {
          logNothingToDerive("selective run");
          return MigrationReport.nothingToDo(
              SelectiveOutcome.clean(settings.mode(), dirty.report(Map.of())), timer.statistics());
        }

        Set<URI> sourceUris =
            timer.time(
                "classify",
                () ->
                    Set.copyOf(classifyResources(vsum, plan, presentNodes, folder).snapshotUris()));
        Repropagation repropagation =
            repropagateInView(vsum, dirty.matchers(), sourceUris, folder, scratch, timer);
        SelectiveOutcome selective =
            SelectiveOutcome.applied(
                settings.mode(),
                dirty.report(repropagation.matchesPerRule()),
                repropagation.affected(),
                repropagation.leftOut(),
                repropagation.sourceElements(),
                repropagation.modelElements());
        if (!repropagation.happened()) {
          return selectiveReport(
              plan,
              context.selection(),
              interaction,
              selective,
              PreservationOutcome.skipped(),
              timer);
        }

        PreservationOutcome preservation =
            preserveUserContentIfEnabled(
                folder, vsum, repropagation.preMigrationFolder(), plan, timer);
        timer.time("refresh", () -> refreshSerializedForms(vsum));
        return selectiveReport(
            plan,
            context.selection(),
            interaction,
            selective,
            recordPreservation(folder, repropagation.preMigrationFolder(), preservation),
            timer);
      } finally {
        vsum.dispose();
      }
    }
  }

  private Repropagation repropagateInView(
      InternalVirtualModel vsum,
      List<DirtyMatcher> matchers,
      Set<URI> sourceUris,
      Path folder,
      ScratchArea scratch,
      PhaseTimer timer) {
    NotRepropagated notRepropagated =
        timer.time(
            "left-out",
            () -> {
              dropResolutionCaches(vsum);
              return NotRepropagated.of(vsum, sourceUris, folder, adapters);
            });
    int modelElements = countModelElements(vsum, folder);
    @SuppressWarnings("resource")
    View view =
        timer.time(
            "view-open",
            () ->
                Vsums.openViewOf(
                    vsum,
                    "selective-repropagation",
                    root -> isOwnedByResourceIn(root, sourceUris)));
    AutoCloseable closing = () -> timer.timeViewClose(view);
    try (closing) {
      CommittableView committable = view.withChangeRecordingTrait();
      AffectedElements.Selection selection =
          timer.time(
              "selection",
              () -> affectedSourceElements(committable, matchers, sourceUris, notRepropagated));
      List<EObject> affected =
          selection.elements().stream().map(AffectedElements.Selected::element).toList();
      requireNoAffectedRoots(affected);
      List<SelectiveOutcome.AffectedElement> described = describe(selection.elements());
      if (affected.isEmpty()) {
        log.info(
            "The dirty rules match no source elements; the persisted registry is left unchanged"
                + " and a rerun will re-detect them.");
        return new Repropagation(
            described,
            selection.matchesPerRule(),
            selection.probedElements(),
            notRepropagated.leftOut(),
            modelElements,
            null);
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
      return new Repropagation(
          described,
          selection.matchesPerRule(),
          selection.probedElements(),
          notRepropagated.leftOut(),
          modelElements,
          preMigrationFolder);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Selective repropagation failed", e);
    }
  }

  private int countModelElements(InternalVirtualModel vsum, Path folder) {
    int count = 0;
    for (Resource resource : List.copyOf(vsum.getViewSourceModels())) {
      if (resource.getURI() == null || !adapters.isMigratedModel(resource.getURI(), folder)) {
        continue;
      }

      for (Iterator<EObject> it = resource.getAllContents(); it.hasNext(); it.next()) {
        count++;
      }
    }

    return count;
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
      SourceSelection selection,
      DetectingUserInteraction interaction,
      SelectiveOutcome selective,
      PreservationOutcome preservation,
      PhaseTimer timer) {
    return new MigrationReport(
        selective.affectedElementCount() > 0,
        plan.sources(),
        plan.derived(),
        selection,
        interaction.getRequestedInteractions(),
        SourceUpdateOutcome.skipped(),
        selective,
        preservation,
        timer.statistics());
  }

  private Optional<PreparedMigration> prepareMigration(
      Path folder, ScratchArea scratch, PhaseTimer timer) {
    InternalVirtualModel sourceVsum =
        timer.time(
            "load",
            () ->
                Vsums.build(
                    folder, specifications.createSpecifications(), new DetectingUserInteraction()));
    LoadedSource source = timer.time("roots", () -> loadSource(sourceVsum));
    try {
      Map<String, List<EObject>> rootsByNsUri = source.rootsByNsUri();
      if (rootsByNsUri.isEmpty()) {
        log.warn(
            "No models found in {}, nothing to migrate. Persist a VSUM there first.",
            folder.toAbsolutePath());
        return Optional.empty();
      }

      log.info("Present models by metamodel: {}", rootsByNsUri.keySet());
      Set<MetamodelNode> presentNodes = presentNodes(rootsByNsUri.keySet());
      if (presentNodes.isEmpty()) {
        throw new IllegalStateException(
            "The change-propagation specifications handle none of the present models "
                + rootsByNsUri.keySet()
                + " - point the migration at a jar whose propagations match these metamodels.");
      }

      RunnerContext context =
          new RunnerContext(
              graph,
              source.vsum(),
              presentNodes,
              adapters,
              new TrialMigration(specifications, adapters, interactionFallback, scratch, folder),
              folder);
      DominancePlan plan =
          timer.time("dominance", () -> new MigrationPlanner(graph, strategy).plan(context));
      log.info("Sources of truth (dominant first): {}", shortNames(plan.sources()));
      if (plan.derived().isEmpty()) {
        logNothingToDerive("full migration");
        return Optional.empty();
      }

      log.info("Re-deriving {} from the sources in place", shortNames(plan.derived()));
      return Optional.of(
          timer.time(
              "snapshot",
              () -> snapshotSources(folder, scratch, source.vsum(), plan, presentNodes, context)));
    } finally {
      source.vsum().dispose();
    }
  }

  private PreparedMigration snapshotSources(
      Path folder,
      ScratchArea scratch,
      InternalVirtualModel sourceVsum,
      DominancePlan plan,
      Set<MetamodelNode> presentNodes,
      RunnerContext context) {
    ResourceClassification classification =
        classifyResources(sourceVsum, plan, presentNodes, folder);
    ModelSnapshot snapshot = ModelSnapshot.of(classification.snapshotUris(), adapters, folder);
    Path preMigrationFolder = copyPreMigrationState(folder, scratch);
    Optional<Path> reusableTrial =
        plan.sources().size() == 1 ? context.trialFolderOf(plan.dominant()) : Optional.empty();
    return new PreparedMigration(
        plan,
        context.selection(),
        reusableTrial,
        snapshot,
        preMigrationFolder,
        copiesOf(preMigrationFolder, folder, classification.derivedFileUris()),
        classification.allModelFileUris());
  }

  private long derivedChangeCount(Path folder, PreparedMigration prepared) {
    try {
      List<Resource> before =
          ModelStates.loadRebased(
              prepared.oldDerivedFiles(), prepared.preMigrationFolder(), folder, adapters);
      List<Resource> after =
          ModelStates.ownedBy(
              ModelStates.load(folder, adapters),
              ModelStates.ownedByAny(prepared.plan().derived()));
      long count = ModelStates.changeCount(new ModelStateDiff(), adapters, before, after);
      log.info(
          "Counted {} change(s) between the old derived models and the re-derived ones", count);
      return count;
    } catch (RuntimeException e) {
      log.warn("The changes of the re-derivation could not be counted: {}", e.getMessage(), e);
      return SourceSelection.NOT_MEASURED;
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
    boolean adopted = prepared.reusableTrial().isPresent();
    if (adopted) {
      timer.time("reuse-trial", () -> adoptTrial(prepared.reusableTrial().get(), folder));
    }

    DetectingUserInteraction reDerivationInteraction =
        new DetectingUserInteraction(interactionFallback);
    InternalVirtualModel targetVsum =
        Vsums.build(folder, specifications.createSpecifications(), reDerivationInteraction);
    SourceSelection selection = prepared.selection();
    SourceUpdateOutcome sourceUpdate;
    PreservationOutcome preservation;
    try {
      if (!adopted) {
        timer.time(
            "re-derive",
            () -> new Replayer(adapters).replayInto(targetVsum, prepared.sourceSnapshot()));
        log.info("In-place re-derivation complete.");
      }
      selection =
          selection.withChangeCount(
              timer.time("change-count", () -> derivedChangeCount(folder, prepared)));
      sourceUpdate =
          timer.time(
              "source-update", () -> updateSourcesIfEnabled(folder, targetVsum, prepared, scratch));
      preservation =
          preserveUserContentIfEnabled(
              folder, targetVsum, prepared.preMigrationFolder(), prepared.plan(), timer);
      timer.time("refresh", () -> refreshSerializedForms(targetVsum));
      logRequestedInteractions(reDerivationInteraction);
    } catch (RuntimeException e) {
      throw new MigrationFailure(prepared.plan(), selection, e);
    } finally {
      targetVsum.dispose();
    }

    return new MigrationReport(
        true,
        prepared.plan().sources(),
        prepared.plan().derived(),
        selection,
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
            preserveOrReport(
                new PreservationContext(
                    targetVsum,
                    folder,
                    preMigrationFolder,
                    adapters,
                    derivedRootPredicate(plan),
                    settings.preservation(),
                    settings.askOnAmbiguity() ? interactionFallback : null)));
  }

  private PreservationOutcome preserveOrReport(PreservationContext context) {
    try {
      return PreservationPass.run(context);
    } catch (RuntimeException e) {
      String reason = e.getMessage() == null ? e.toString() : e.getMessage();
      log.warn(
          "The preservation pass did not complete; the re-derived models are kept as they are: {}",
          reason,
          e);
      return PreservationOutcome.failed(settings.preservation(), reason);
    }
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
    for (EObject root : Vsums.liveRoots(vsum)) {
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

    snapshotUris.removeAll(derivedUris);
    return new ResourceClassification(
        List.copyOf(snapshotUris), List.copyOf(derivedUris), List.copyOf(allFileUris));
  }

  private record CurrentRules(
      RuleHashRegistry registry, List<ChangePropagationSpecification> specifications) {}

  private record LoadedSource(InternalVirtualModel vsum, Map<String, List<EObject>> rootsByNsUri) {}

  private record DirtyRules(
      RuleHashDiff diff,
      SortedSet<ConsistencyRuleId> ids,
      List<DirtyMatcher> matchers,
      int persistedCount,
      int currentCount,
      List<ChangePropagationSpecification> specifications) {
    boolean isEmpty() {
      return ids.isEmpty();
    }

    SelectiveOutcome.RuleDiff report(Map<ConsistencyRuleId, Integer> matchesPerRule) {
      List<SelectiveOutcome.DirtyRule> dirty = new ArrayList<>();
      for (ConsistencyRuleId id : ids) {
        dirty.add(
            new SelectiveOutcome.DirtyRule(
                id.value(), kindOf(id), matchesPerRule.getOrDefault(id, 0)));
      }

      return new SelectiveOutcome.RuleDiff(persistedCount, currentCount, List.copyOf(dirty));
    }

    private SelectiveOutcome.DirtyRule.Kind kindOf(ConsistencyRuleId id) {
      if (diff.added().contains(id)) {
        return SelectiveOutcome.DirtyRule.Kind.ADDED;
      }
      if (diff.removed().contains(id)) {
        return SelectiveOutcome.DirtyRule.Kind.REMOVED;
      }

      return SelectiveOutcome.DirtyRule.Kind.CHANGED;
    }
  }
}
