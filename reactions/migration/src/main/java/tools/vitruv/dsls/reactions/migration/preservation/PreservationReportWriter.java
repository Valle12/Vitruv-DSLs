package tools.vitruv.dsls.reactions.migration.preservation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class PreservationReportWriter {
  public static final String FILE_NAME = "preservation-report.md";

  private static final String OPEN_DECISIONS_INTRO =
      """
      Choices this run could not make alone, so nothing was done about them; what they \
      concern is work left over.

      """;

  private static final String NOTES_INTRO =
      """
      How existing content travelled through the migration - moves, values the new rules \
      produce differently, containers restored only as carriers. Nothing here needs \
      action, and none of it counts towards the number above.

      """;

  private static final String KEPT_INTRO_APPLIED =
      """
      Content no rule of the new specifications derives - hand-written above all. It \
      was re-attached where each item says.

      """;

  private static final String KEPT_INTRO_RECORDED =
      """
      Content no rule of the new specifications derives - hand-written above all. \
      This run only recorded it; `--preserve user` is the run that re-attaches \
      it.

      """;

  private static final String NOT_KEPT_INTRO =
      """
      Content that could not be placed in the migrated models; each item names the reason. \
      Putting it back is manual work.

      """;

  private PreservationReportWriter() {}

  public static Path write(Path folder, PreservationOutcome outcome, Location location) {
    Path file = folder.resolve(FILE_NAME);
    try {
      Files.writeString(file, render(outcome, location));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    return file;
  }

  static String render(PreservationOutcome outcome, Location location) {
    StringBuilder report = new StringBuilder("# Migration preservation report\n\n");
    report
        .append("Policy: ")
        .append(outcome.policy().name().toLowerCase(Locale.ROOT))
        .append(outcome.applied() ? " (applied)" : " (analysed only)")
        .append("\n");
    appendSummary(report, outcome);
    appendPreserved(report, outcome);
    appendLost(report, outcome, location);
    appendOpenDecisions(report, outcome);
    appendNotes(report, outcome);
    return report.toString();
  }

  private static void appendSummary(StringBuilder report, PreservationOutcome outcome) {
    int kept = outcome.preserved().size();
    int lost = outcome.lost().size();
    int open = outcome.openDecisions().size();
    report.append("Left to deal with by hand: ").append(outcome.manualItems());
    if (outcome.applied()) {
      report
          .append(" (")
          .append(lost)
          .append(" not kept + ")
          .append(open)
          .append(open == 1 ? " open decision); the " : " open decisions); the ")
          .append(kept)
          .append(kept == 1 ? " kept item was re-attached." : " kept items were re-attached.")
          .append("\n\n");
    } else {
      report
          .append(" (")
          .append(kept)
          .append(" would be kept + ")
          .append(lost)
          .append(" not kept + ")
          .append(open)
          .append(open == 1 ? " open decision)" : " open decisions)")
          .append("\n\n");
    }
  }

  private static void appendOpenDecisions(StringBuilder report, PreservationOutcome outcome) {
    List<DecisionItem> open = outcome.openDecisions();
    if (open.isEmpty()) {
      return;
    }

    report.append("\n## Open decisions (").append(open.size()).append(")\n\n");
    report.append(OPEN_DECISIONS_INTRO);
    appendDecisionItems(report, open);
  }

  private static void appendNotes(StringBuilder report, PreservationOutcome outcome) {
    List<DecisionItem> notes = outcome.notes();
    if (notes.isEmpty()) {
      return;
    }

    report.append("\n## Notes (").append(notes.size()).append(")\n\n");
    report.append(NOTES_INTRO);
    appendDecisionItems(report, notes);
  }

  private static void appendDecisionItems(StringBuilder report, List<DecisionItem> items) {
    for (DecisionItem item : items) {
      report
          .append("- ")
          .append(item.subject())
          .append("\n  ")
          .append(item.kind().getDescription());
      if (item.detail() != null) {
        report.append(": ").append(item.detail());
      }

      report.append("\n");
    }
  }

  private static void appendWhereToRecoverFrom(
      StringBuilder report, PreservationOutcome outcome, Location location) {
    if (location == Location.RECOVERY_FOLDER) {
      report.append("Recover these from the models next to this report.\n\n");
      return;
    }

    outcome
        .recovery()
        .ifPresent(
            folder ->
                report
                    .append("Recover these from the models in `")
                    .append(folder.toAbsolutePath())
                    .append("`, not from the ones next to this report - those are the migrated")
                    .append(" ones.\n\n"));
  }

  private static void appendPreserved(StringBuilder report, PreservationOutcome outcome) {
    report
        .append(outcome.applied() ? "## Kept (" : "## Would be kept (")
        .append(outcome.preserved().size())
        .append(")\n\n");
    if (!outcome.preserved().isEmpty()) {
      report.append(outcome.applied() ? KEPT_INTRO_APPLIED : KEPT_INTRO_RECORDED);
    }

    for (PreservedItem item : outcome.preserved()) {
      report
          .append("- ")
          .append(item.element())
          .append("\n  into ")
          .append(item.target())
          .append(" (")
          .append(item.feature())
          .append(")\n");
    }

    report.append("\n");
  }

  private static void appendLost(
      StringBuilder report, PreservationOutcome outcome, Location location) {
    report.append("## Not kept (").append(outcome.lost().size()).append(")\n\n");
    if (!outcome.lost().isEmpty()) {
      report.append(NOT_KEPT_INTRO);
      appendWhereToRecoverFrom(report, outcome, location);
    }

    for (LostItem item : outcome.lost()) {
      report
          .append("- ")
          .append(item.element())
          .append("\n  ")
          .append(item.reason().getDescription());
      if (item.detail() != null) {
        report.append(": ").append(item.detail());
      }

      report.append("\n");
    }
  }

  public enum Location {
    RECOVERY_FOLDER,
    VSUM_FOLDER
  }
}
