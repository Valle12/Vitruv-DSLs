package tools.vitruv.dsls.reactions.migration.preservation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.dsls.reactions.migration.preservation.PreservationReportWriter.Location;

class PreservationReportWriterTest {
  @TempDir Path tempDir;

  @Test
  @DisplayName("an analysing run splits open decisions from notes and states the formula")
  void test1() {
    PreservationOutcome outcome =
        new PreservationOutcome(
            PreservationPolicy.REPORT,
            List.of(
                new PreservedItem("first", "target", "feature"),
                new PreservedItem("second", "target", "feature")),
            List.of(new LostItem("third", LossReason.NO_COMPATIBLE_FEATURE, "no slot")),
            List.of(
                DecisionItem.unresolved("fourth", "which one"),
                DecisionItem.carrier("fifth", null),
                DecisionItem.replaced("sixth", "another value")),
            null);

    String report = PreservationReportWriter.render(outcome, Location.VSUM_FOLDER);

    assertTrue(report.contains("Policy: report (analysed only)"));
    assertTrue(
        report.contains(
            "Left to deal with by hand: 4 (2 would be kept + 1 not kept + 1 open decision)"),
        report);
    assertTrue(report.contains("## Would be kept (2)"));
    assertTrue(report.contains("## Not kept (1)"));
    assertTrue(report.contains("## Open decisions (1)"));
    assertTrue(report.contains("## Notes (2)"));
    assertFalse(report.contains("## Decisions ("), "the mixed section is gone: " + report);
    assertTrue(
        report.indexOf("## Open decisions") < report.indexOf("## Notes"),
        "open questions come before the informational rest");
  }

  @Test
  @DisplayName("an applied run counts only what was not handled")
  void test2() {
    PreservationOutcome outcome =
        new PreservationOutcome(
            PreservationPolicy.USER,
            List.of(new PreservedItem("first", "target", "feature")),
            List.of(new LostItem("second", LossReason.RULE_NO_LONGER_DERIVES, null)),
            List.of(DecisionItem.unresolved("third", "which one")),
            null);

    String report = PreservationReportWriter.render(outcome, Location.VSUM_FOLDER);

    assertTrue(report.contains("Policy: user (applied)"));
    assertTrue(report.contains("## Kept (1)"));
    assertTrue(
        report.contains(
            "Left to deal with by hand: 2 (1 not kept + 1 open decision); the 1 kept item was"
                + " re-attached."),
        report);
  }

  @Test
  @DisplayName("a run with nothing to tell renders no decision sections at all")
  void test3() {
    PreservationOutcome outcome =
        new PreservationOutcome(PreservationPolicy.REPORT, List.of(), List.of(), List.of(), null);

    String report = PreservationReportWriter.render(outcome, Location.VSUM_FOLDER);

    assertTrue(
        report.contains(
            "Left to deal with by hand: 0 (0 would be kept + 0 not kept + 0 open decisions)"),
        report);
    assertTrue(report.contains("## Would be kept (0)"));
    assertTrue(report.contains("## Not kept (0)"));
    assertFalse(report.contains("## Open decisions"));
    assertFalse(report.contains("## Notes"));
  }

  @Test
  @DisplayName("the recovery pointers stay word for word what other code and prose rely on")
  void test4() {
    Path recovery = tempDir.resolve("vsum-premigration-1");
    PreservationOutcome outcome =
        new PreservationOutcome(
                PreservationPolicy.REPORT,
                List.of(),
                List.of(new LostItem("gone", LossReason.NO_COUNTERPART_CONTAINER, null)),
                List.of(),
                null)
            .recoverableFrom(recovery);

    String vsumReport = PreservationReportWriter.render(outcome, Location.VSUM_FOLDER);
    String recoveryReport = PreservationReportWriter.render(outcome, Location.RECOVERY_FOLDER);

    assertTrue(
        vsumReport.contains(
            "Recover these from the models in `"
                + recovery.toAbsolutePath()
                + "`, not from the ones next to this report - those are the migrated ones."),
        vsumReport);
    assertTrue(
        recoveryReport.contains("Recover these from the models next to this report."),
        recoveryReport);
  }
}
