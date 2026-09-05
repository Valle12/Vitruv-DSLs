package tools.vitruv.dsls.reactions.migration.migration;

import java.util.List;
import java.util.Optional;

public record SelectiveOutcome(
    MigrationMode mode,
    boolean attempted,
    boolean fellBackToFull,
    Optional<String> fallbackReason,
    RuleDiff rules,
    List<AffectedElement> affectedElements,
    List<LeftOutElement> leftOutElements,
    int sourceElements,
    int modelElements) {

  public static SelectiveOutcome notRequested() {
    return new SelectiveOutcome(
        MigrationMode.FULL,
        false,
        false,
        Optional.empty(),
        RuleDiff.none(),
        List.of(),
        List.of(),
        0,
        0);
  }

  public static SelectiveOutcome clean(MigrationMode mode, RuleDiff rules) {
    return new SelectiveOutcome(
        mode, true, false, Optional.empty(), rules, List.of(), List.of(), 0, 0);
  }

  public static SelectiveOutcome applied(
      MigrationMode mode,
      RuleDiff rules,
      List<AffectedElement> affectedElements,
      List<LeftOutElement> leftOutElements,
      int sourceElements,
      int modelElements) {
    return new SelectiveOutcome(
        mode,
        true,
        false,
        Optional.empty(),
        rules,
        List.copyOf(affectedElements),
        List.copyOf(leftOutElements),
        sourceElements,
        modelElements);
  }

  public static SelectiveOutcome fellBack(MigrationMode mode, String reason) {
    return new SelectiveOutcome(
        mode, true, true, Optional.of(reason), RuleDiff.none(), List.of(), List.of(), 0, 0);
  }

  public int dirtyRuleCount() {
    return rules.dirty().size();
  }

  public int affectedElementCount() {
    return affectedElements.size();
  }

  public long matchedElementCount() {
    return affectedElements.stream().filter(element -> element.refersTo().isEmpty()).count();
  }

  public long referrerElementCount() {
    return affectedElements.size() - matchedElementCount();
  }

  public record RuleDiff(int persisted, int current, List<DirtyRule> dirty) {
    public static RuleDiff none() {
      return new RuleDiff(0, 0, List.of());
    }

    public long count(DirtyRule.Kind kind) {
      return dirty.stream().filter(rule -> rule.kind() == kind).count();
    }

    public long matching() {
      return dirty.stream().filter(rule -> rule.matchedElements() > 0).count();
    }
  }

  public record DirtyRule(String id, Kind kind, int matchedElements) {
    public enum Kind {
      ADDED("+"),
      REMOVED("-"),
      CHANGED("~");

      private final String marker;

      Kind(String marker) {
        this.marker = marker;
      }

      public String marker() {
        return marker;
      }
    }
  }

  public record AffectedElement(
      String key, String metaclass, List<String> matchedBy, Optional<String> refersTo) {
    public String selectedBy() {
      return refersTo
          .map(target -> "refers to " + target)
          .orElseGet(() -> "matched by " + String.join(", ", matchedBy));
    }
  }

  public record LeftOutElement(String key, String metaclass, String reason) {}
}
