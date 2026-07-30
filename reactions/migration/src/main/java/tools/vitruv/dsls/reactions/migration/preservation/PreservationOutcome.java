package tools.vitruv.dsls.reactions.migration.preservation;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record PreservationOutcome(
    PreservationPolicy policy,
    List<PreservedItem> preserved,
    List<LostItem> lost,
    List<DecisionItem> decisions,
    Path recoveryFolder) {
  public static PreservationOutcome skipped() {
    return new PreservationOutcome(PreservationPolicy.OFF, List.of(), List.of(), List.of(), null);
  }

  public PreservationOutcome recoverableFrom(Path folder) {
    return new PreservationOutcome(policy, preserved, lost, decisions, folder);
  }

  public boolean attempted() {
    return policy.analyses();
  }

  public boolean applied() {
    return !policy.leavesModelsUnchanged() && !preserved.isEmpty();
  }

  public List<DecisionItem> openDecisions() {
    return decisions.stream()
        .filter(decision -> decision.kind() == DecisionItem.Kind.UNRESOLVED)
        .toList();
  }

  public List<DecisionItem> notes() {
    return decisions.stream()
        .filter(decision -> decision.kind() != DecisionItem.Kind.UNRESOLVED)
        .toList();
  }

  public int manualItems() {
    int recordedOnly = policy.leavesModelsUnchanged() ? preserved.size() : 0;
    return recordedOnly + lost.size() + openDecisions().size();
  }

  public Optional<Path> recovery() {
    return Optional.ofNullable(recoveryFolder);
  }
}
