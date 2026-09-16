package tools.vitruv.dsls.reactions.migration.preservation;

/**
 * One value that could not be carried over.
 *
 * @param element what was lost, described as a report names it
 * @param reason why it could not be kept
 * @param detail what exactly stood in the way, {@code null} when the reason says it all
 */
public record LostItem(String element, LossReason reason, String detail) {
  /**
   * Returns a loss that needs no further detail.
   *
   * @param element what was lost
   * @param reason why it could not be kept
   * @return the recorded loss
   */
  public static LostItem of(String element, LossReason reason) {
    return new LostItem(element, reason, null);
  }
}
