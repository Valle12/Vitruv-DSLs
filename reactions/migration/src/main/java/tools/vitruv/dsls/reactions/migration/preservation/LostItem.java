package tools.vitruv.dsls.reactions.migration.preservation;

public record LostItem(String element, LossReason reason, String detail) {
  public static LostItem of(String element, LossReason reason) {
    return new LostItem(element, reason, null);
  }
}
