package tools.vitruv.dsls.reactions.migration.preservation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;

@Slf4j
public final class PreservationPass {
  private PreservationPass() {}

  public static PreservationOutcome run(PreservationContext context) {
    PreservationPolicy policy = context.policy();
    if (!policy.analyses()) {
      return PreservationOutcome.skipped();
    }

    VsumState oldState = VsumState.load(context.preMigrationFolder(), context.adapters());
    VsumState newState = VsumState.load(context.vsumFolder(), context.adapters());
    AmbiguityResolver resolver = resolverFor(context);
    CounterpartIndex counterparts =
        CounterpartIndex.build(oldState, newState, context.derivedRoot(), resolver);
    ExternalTargets externalTargets =
        new ExternalTargets(context.adapters(), context.preMigrationFolder(), context.vsumFolder());
    CollectedContent content =
        new CandidateCollector(
                oldState,
                newState,
                counterparts,
                policy,
                context.derivedRoot(),
                resolver,
                externalTargets)
            .collect();
    log.info(
        "Preservation ({}): {} counterpart(s) matched, {} candidate(s), {} orphaned file(s),"
            + " {} item(s) that cannot be kept",
        policy,
        counterparts.size(),
        content.candidates().size(),
        content.orphanResources().size(),
        content.losses().size());
    List<DecisionItem> decisions = new ArrayList<>(content.decisions());
    decisions.addAll(resolver.decisions());
    if (policy.leavesModelsUnchanged()) {
      return analyse(context, oldState, newState, counterparts, content, decisions);
    }

    return reattach(context, oldState, counterparts, content, decisions);
  }

  private static AmbiguityResolver resolverFor(PreservationContext context) {
    Path oldFolder = context.preMigrationFolder();
    Path newFolder = context.vsumFolder();
    return context.interaction() == null || context.policy().leavesModelsUnchanged()
        ? AmbiguityResolver.reporting(oldFolder, newFolder)
        : AmbiguityResolver.asking(context.interaction(), oldFolder, newFolder);
  }

  private static PreservationOutcome applyWith(
      TransplantTarget target,
      PreservationContext context,
      VsumState oldState,
      CounterpartIndex counterparts,
      CollectedContent content,
      List<DecisionItem> decisions) {
    TransplantApplier applier =
        new TransplantApplier(
            target,
            counterparts,
            context.adapters(),
            context.preMigrationFolder(),
            context.vsumFolder(),
            oldState::isCorresponded);
    applier.apply(content);
    List<LostItem> lost = new ArrayList<>(content.losses());
    lost.addAll(applier.lost());
    List<DecisionItem> allDecisions = new ArrayList<>(decisions);
    allDecisions.addAll(applier.decisions());
    return new PreservationOutcome(
        context.policy(), applier.preserved(), lost, allDecisions, null, null);
  }

  private static PreservationOutcome analyse(
      PreservationContext context,
      VsumState oldState,
      VsumState newState,
      CounterpartIndex counterparts,
      CollectedContent content,
      List<DecisionItem> decisions) {
    return applyWith(
        new AnalysisTransplantTarget(newState),
        context,
        oldState,
        counterparts,
        content,
        decisions);
  }

  private static PreservationOutcome reattach(
      PreservationContext context,
      VsumState oldState,
      CounterpartIndex counterparts,
      CollectedContent content,
      List<DecisionItem> decisions) {
    PreservationPolicy policy = context.policy();
    if (content.candidates().isEmpty() && content.orphanResources().isEmpty()) {
      return new PreservationOutcome(policy, List.of(), content.losses(), decisions, null, null);
    }

    Path vsumFolder = context.vsumFolder();
    try (View view = Vsums.openViewOfAll(context.vsum(), "preservation")) {
      CommittableView committable = view.withChangeRecordingTrait();
      PreservationOutcome outcome =
          applyWith(
              new ViewTransplantTarget(committable, ViewIndex.of(committable, vsumFolder)),
              context,
              oldState,
              counterparts,
              content,
              decisions);
      if (outcome.applied()) {
        commit(committable);
      }

      return outcome;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Closing the preservation view failed", e);
    }
  }

  private static void commit(CommittableView committable) {
    try {
      committable.commitChanges();
    } catch (IllegalArgumentException noConcreteChange) {
      log.info("The re-attached content produced no changes.");
    }
  }
}
