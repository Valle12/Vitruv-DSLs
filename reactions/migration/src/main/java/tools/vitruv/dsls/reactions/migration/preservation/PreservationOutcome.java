package tools.vitruv.dsls.reactions.migration.preservation;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * What the preservation step carried over, what it could not keep and what was decided.
 *
 * @param policy the policy the step ran under
 * @param preserved the values it carried over, or under a reporting policy the ones it could
 *     have carried over
 * @param lost the values that cannot be kept, each with the reason
 * @param decisions what was settled and what nobody could settle
 * @param recoveryFolder where the state from before the migration was kept, {@code null}
 *     when it was not kept
 * @param failure why the step could not finish, {@code null} when it did
 */
public record PreservationOutcome(
    PreservationPolicy policy,
    List<PreservedItem> preserved,
    List<LostItem> lost,
    List<DecisionItem> decisions,
    Path recoveryFolder,
    String failure) {
  /**
   * Returns the outcome of a run that preserved nothing.
   *
   * @return an outcome under the off policy
   */
  public static PreservationOutcome skipped() {
    return new PreservationOutcome(
        PreservationPolicy.OFF, List.of(), List.of(), List.of(), null, null);
  }

  /**
   * Returns the outcome of a step that could not finish.
   *
   * @param policy the policy it ran under
   * @param reason what went wrong
   * @return the failed outcome
   */
  public static PreservationOutcome failed(PreservationPolicy policy, String reason) {
    return new PreservationOutcome(policy, List.of(), List.of(), List.of(), null, reason);
  }

  /**
   * Returns the same outcome, naming the folder that holds the state from before the migration.
   *
   * @param folder where that state was kept
   * @return the changed outcome
   */
  public PreservationOutcome recoverableFrom(Path folder) {
    return new PreservationOutcome(policy, preserved, lost, decisions, folder, failure);
  }

  /**
   * Returns whether the preservation step ran at all.
   *
   * @return whether its policy works anything out
   */
  public boolean attempted() {
    return policy.analyses();
  }

  /**
   * Returns whether the step ended in a failure.
   *
   * @return whether a reason was recorded
   */
  public boolean failed() {
    return failure != null;
  }

  /**
   * Returns whether anything was actually put back into a model.
   *
   * @return whether the policy changes models and something was preserved
   */
  public boolean applied() {
    return !policy.leavesModelsUnchanged() && !preserved.isEmpty();
  }

  /**
   * Returns the questions nobody could answer.
   *
   * @return the unresolved decisions
   */
  public List<DecisionItem> openDecisions() {
    return decisions.stream()
        .filter(decision -> decision.kind() == DecisionItem.Kind.UNRESOLVED)
        .toList();
  }

  /**
   * Returns the decisions that were settled.
   *
   * @return every decision but the unresolved ones
   */
  public List<DecisionItem> notes() {
    return decisions.stream()
        .filter(decision -> decision.kind() != DecisionItem.Kind.UNRESOLVED)
        .toList();
  }

  /**
   * Returns how many entries of the report have to be dealt with by hand. Under a policy that
   * only reports, the values that could have been carried over count as well, since none of
   * them was put back.
   *
   * @return the number of manual items
   */
  public int manualItems() {
    int recordedOnly = policy.leavesModelsUnchanged() ? preserved.size() : 0;
    return recordedOnly + lost.size() + openDecisions().size();
  }

  /**
   * Returns where the state from before the migration was kept.
   *
   * @return the recovery folder, empty when no copy was kept
   */
  public Optional<Path> recovery() {
    return Optional.ofNullable(recoveryFolder);
  }
}
