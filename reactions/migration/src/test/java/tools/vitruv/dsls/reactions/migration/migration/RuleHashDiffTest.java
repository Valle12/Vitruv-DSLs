package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.vitruv.change.propagation.ConsistencyRuleId;

class RuleHashDiffTest {
  private static final ConsistencyRuleId KEPT = ConsistencyRuleId.of("mockup::Kept");
  private static final ConsistencyRuleId EDITED = ConsistencyRuleId.of("mockup::Edited");
  private static final ConsistencyRuleId REMOVED = ConsistencyRuleId.of("mockup::Removed");
  private static final ConsistencyRuleId ADDED = ConsistencyRuleId.of("mockup::Added");

  private static RuleHashDiff diff() {
    return RuleHashDiff.between(
        Map.of(KEPT, "same", EDITED, "before", REMOVED, "gone"),
        Map.of(KEPT, "same", EDITED, "after", ADDED, "new"));
  }

  @Test
  @DisplayName("partitions ids into added, removed, and changed")
  void test1() {
    RuleHashDiff diff = diff();

    assertEquals(Set.of(ADDED), diff.added());
    assertEquals(Set.of(REMOVED), diff.removed());
    assertEquals(Set.of(EDITED), diff.changed());
  }

  @Test
  @DisplayName("id mode treats only added and removed ids as dirty")
  void test2() {
    assertEquals(Set.of(ADDED, REMOVED), diff().dirtyIds(MigrationMode.ID_DIFF));
  }

  @Test
  @DisplayName("hash mode additionally treats changed hashes as dirty")
  void test3() {
    assertEquals(Set.of(ADDED, REMOVED, EDITED), diff().dirtyIds(MigrationMode.HASH_DIFF));
  }

  @Test
  @DisplayName("identical maps produce no dirty ids")
  void test4() {
    RuleHashDiff diff = RuleHashDiff.between(Map.of(KEPT, "same"), Map.of(KEPT, "same"));

    assertEquals(Set.of(), diff.dirtyIds(MigrationMode.ID_DIFF));
    assertEquals(Set.of(), diff.dirtyIds(MigrationMode.HASH_DIFF));
  }
}
