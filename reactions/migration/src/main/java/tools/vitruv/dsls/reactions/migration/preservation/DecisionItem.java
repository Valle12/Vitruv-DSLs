package tools.vitruv.dsls.reactions.migration.preservation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * One thing the preservation step settled, or could not settle.
 *
 * @param kind what sort of decision it was
 * @param subject what the decision was about
 * @param detail what exactly was decided or what stood in the way
 */
public record DecisionItem(Kind kind, String subject, String detail) {
  /**
   * Records that the new rules keep the value somewhere other than the old ones did.
   *
   * @param subject what the decision is about
   * @param detail where the value now sits
   * @return the recorded decision
   */
  public static DecisionItem moved(String subject, String detail) {
    return new DecisionItem(Kind.MOVED, subject, detail);
  }

  /**
   * Records that the new rules derive a different value than the old ones did.
   *
   * @param subject what the decision is about
   * @param detail which value took the place of which
   * @return the recorded decision
   */
  public static DecisionItem replaced(String subject, String detail) {
    return new DecisionItem(Kind.REPLACED, subject, detail);
  }

  /**
   * Records that a handwritten value was kept in place of the derived one.
   *
   * @param subject what the decision is about
   * @param detail which value was overridden
   * @return the recorded decision
   */
  public static DecisionItem overridden(String subject, String detail) {
    return new DecisionItem(Kind.OVERRIDDEN, subject, detail);
  }

  /**
   * Records that an element was restored only so that handwritten content below it could be.
   *
   * @param subject what the decision is about
   * @param detail what the element was restored for
   * @return the recorded decision
   */
  public static DecisionItem carrier(String subject, String detail) {
    return new DecisionItem(Kind.CARRIER, subject, detail);
  }

  /**
   * Records a question the caller answered.
   *
   * @param subject what the question was about
   * @param detail what was chosen
   * @return the recorded decision
   */
  public static DecisionItem answered(String subject, String detail) {
    return new DecisionItem(Kind.ANSWERED, subject, detail);
  }

  /**
   * Records a question nobody answered, which the report leaves as work to be done by hand.
   *
   * @param subject what the question was about
   * @param detail what the alternatives were
   * @return the recorded decision
   */
  public static DecisionItem unresolved(String subject, String detail) {
    return new DecisionItem(Kind.UNRESOLVED, subject, detail);
  }

  /**
   * What sort of decision was recorded. Each kind carries the wording the preservation report
   * uses for it.
   */
  @Getter
  @RequiredArgsConstructor
  public enum Kind {
    MOVED("the new rules persist it elsewhere"),
    REPLACED("the new rules produce a different value"),
    OVERRIDDEN("the hand-written value replaced the derived one"),
    CARRIER("restored only to carry hand-written content"),
    ANSWERED("decided by the user"),
    UNRESOLVED("nobody could decide");

    private final String description;
  }
}
