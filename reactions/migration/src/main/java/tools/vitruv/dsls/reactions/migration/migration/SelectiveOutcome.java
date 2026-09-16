package tools.vitruv.dsls.reactions.migration.migration;

import java.util.List;
import java.util.Optional;

/**
 * What the selective path of a migration found and did.
 *
 * @param mode the mode the rule comparison ran in
 * @param attempted whether the rules were compared at all
 * @param fellBackToFull whether the selective path gave way to a full migration
 * @param fallbackReason why it gave way, empty when it did not
 * @param rules how many rules were recorded, how many the new rule set holds, and which of
 *     them are dirty
 * @param affectedElements the elements that were torn down and repropagated
 * @param leftOutElements the elements a dirty rule matched that were left alone
 * @param sourceElements how many elements the models of the dominant metamodel held
 * @param modelElements how many elements the V-SUM held altogether
 */
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

  /**
   * Returns the outcome of a run that never compared rules, because a full migration was asked
   * for from the start.
   *
   * @return an outcome recording that nothing was attempted
   */
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

  /**
   * Returns the outcome of a comparison that found no dirty rule.
   *
   * @param mode the mode the comparison ran in
   * @param rules what the comparison counted
   * @return an outcome recording that there was nothing to repropagate
   */
  public static SelectiveOutcome clean(MigrationMode mode, RuleDiff rules) {
    return new SelectiveOutcome(
        mode, true, false, Optional.empty(), rules, List.of(), List.of(), 0, 0);
  }

  /**
   * Returns the outcome of a selective run that repropagated.
   *
   * @param mode the mode the comparison ran in
   * @param rules what the comparison counted
   * @param affectedElements the elements that were torn down and repropagated
   * @param leftOutElements the elements a dirty rule matched that were left alone
   * @param sourceElements how many elements the models of the dominant metamodel held
   * @param modelElements how many elements the V-SUM held altogether
   * @return the outcome of the run
   */
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

  /**
   * Returns the outcome of a selective run that gave way to a full migration.
   *
   * @param mode the mode the run was asked for
   * @param reason why the selective path could not be taken
   * @return the outcome of the run
   */
  public static SelectiveOutcome fellBack(MigrationMode mode, String reason) {
    return new SelectiveOutcome(
        mode, true, true, Optional.of(reason), RuleDiff.none(), List.of(), List.of(), 0, 0);
  }

  /**
   * Returns how many rules the comparison called dirty.
   *
   * @return the number of dirty rules
   */
  public int dirtyRuleCount() {
    return rules.dirty().size();
  }

  /**
   * Returns how many elements were torn down and repropagated.
   *
   * @return the number of affected elements
   */
  public int affectedElementCount() {
    return affectedElements.size();
  }

  /**
   * Returns how many affected elements a dirty rule matched itself.
   *
   * @return the number of matched elements
   */
  public long matchedElementCount() {
    return affectedElements.stream().filter(element -> element.refersTo().isEmpty()).count();
  }

  /**
   * Returns how many affected elements were taken along only because they refer to a matched
   * one.
   *
   * @return the number of referring elements
   */
  public long referrerElementCount() {
    return affectedElements.size() - matchedElementCount();
  }

  /**
   * How the rules a V-SUM recorded compare to the rules of the new rule set.
   *
   * @param persisted how many rules the V-SUM had recorded
   * @param current how many rules the new rule set holds
   * @param dirty the rules that differ between the two
   */
  public record RuleDiff(int persisted, int current, List<DirtyRule> dirty) {
    /**
     * Returns the comparison of two rule sets that hold nothing.
     *
     * @return a difference without any rule
     */
    public static RuleDiff none() {
      return new RuleDiff(0, 0, List.of());
    }

    /**
     * Returns how many dirty rules differ in the given way.
     *
     * @param kind the kind of difference to count
     * @return the number of dirty rules of that kind
     */
    public long count(DirtyRule.Kind kind) {
      return dirty.stream().filter(rule -> rule.kind() == kind).count();
    }

    /**
     * Returns how many dirty rules matched an element at all, which is the part of the
     * comparison that the migration acted on.
     *
     * @return the number of dirty rules with at least one match
     */
    public long matching() {
      return dirty.stream().filter(rule -> rule.matchedElements() > 0).count();
    }
  }

  /**
   * One rule that differs between the recorded state and the new rule set.
   *
   * @param id the identifier of the rule
   * @param kind how it differs
   * @param matchedElements how many elements its trigger matched
   */
  public record DirtyRule(String id, Kind kind, int matchedElements) {
    /** In which way a dirty rule differs from what the V-SUM recorded. */
    public enum Kind {
      /** The new rule set holds the rule and the recorded state did not. */
      ADDED("+"),
      /** The recorded state held the rule and the new rule set does not. */
      REMOVED("-"),
      /** Both hold the rule, but its semantic hash differs. */
      CHANGED("~");

      private final String marker;

      Kind(String marker) {
        this.marker = marker;
      }

      /**
       * Returns the sign a report writes this kind as.
       *
       * @return the marker of the kind
       */
      public String marker() {
        return marker;
      }
    }
  }

  /**
   * One element the selective path tore down and repropagated.
   *
   * @param key how the element is named in a report
   * @param metaclass the metaclass of the element
   * @param matchedBy the dirty rules whose trigger matched it
   * @param refersTo the affected element it was taken along for, empty when a rule matched it
   *     itself
   */
  public record AffectedElement(
      String key, String metaclass, List<String> matchedBy, Optional<String> refersTo) {
    /**
     * Returns why this element was repropagated, phrased for a report.
     *
     * @return either the rules that matched it or the element it refers to
     */
    public String selectedBy() {
      return refersTo
          .map(target -> "refers to " + target)
          .orElseGet(() -> "matched by " + String.join(", ", matchedBy));
    }
  }

  /**
   * One element a dirty rule matched that was left alone, together with the reason.
   *
   * @param key how the element is named in a report
   * @param metaclass the metaclass of the element
   * @param reason why it was not repropagated
   */
  public record LeftOutElement(String key, String metaclass, String reason) {}
}
