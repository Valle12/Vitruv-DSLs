package tools.vitruv.dsls.reactions.migration.preservation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

public record DecisionItem(Kind kind, String subject, String detail) {
  public static DecisionItem moved(String subject, String detail) {
    return new DecisionItem(Kind.MOVED, subject, detail);
  }

  public static DecisionItem replaced(String subject, String detail) {
    return new DecisionItem(Kind.REPLACED, subject, detail);
  }

  public static DecisionItem overridden(String subject, String detail) {
    return new DecisionItem(Kind.OVERRIDDEN, subject, detail);
  }

  public static DecisionItem carrier(String subject, String detail) {
    return new DecisionItem(Kind.CARRIER, subject, detail);
  }

  public static DecisionItem answered(String subject, String detail) {
    return new DecisionItem(Kind.ANSWERED, subject, detail);
  }

  public static DecisionItem unresolved(String subject, String detail) {
    return new DecisionItem(Kind.UNRESOLVED, subject, detail);
  }

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
