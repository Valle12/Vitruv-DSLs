package tools.vitruv.dsls.reactions.migration.migration;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import tools.vitruv.change.propagation.ConsistencyRuleId;
import tools.vitruv.dsls.reactions.migration.migration.SelectiveOutcome.AffectedElement;
import tools.vitruv.dsls.reactions.migration.migration.SelectiveOutcome.LeftOutElement;

record Repropagation(
    List<AffectedElement> affected,
    Map<ConsistencyRuleId, Integer> matchesPerRule,
    int sourceElements,
    List<LeftOutElement> leftOut,
    int modelElements,
    Path preMigrationFolder) {

  boolean happened() {
    return !affected.isEmpty();
  }
}
