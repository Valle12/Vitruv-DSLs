package tools.vitruv.dsls.reactions.migration.migration;

import java.util.Collections;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import tools.vitruv.change.propagation.ConsistencyRuleId;

record RuleHashDiff(
    SortedSet<ConsistencyRuleId> added,
    SortedSet<ConsistencyRuleId> removed,
    SortedSet<ConsistencyRuleId> changed) {

  static RuleHashDiff between(
      Map<ConsistencyRuleId, String> persistedHashes,
      Map<ConsistencyRuleId, String> currentHashes) {
    SortedSet<ConsistencyRuleId> added = new TreeSet<>(currentHashes.keySet());
    added.removeAll(persistedHashes.keySet());
    SortedSet<ConsistencyRuleId> removed = new TreeSet<>(persistedHashes.keySet());
    removed.removeAll(currentHashes.keySet());
    SortedSet<ConsistencyRuleId> changed = new TreeSet<>();
    for (var persistedEntry : persistedHashes.entrySet()) {
      String currentHash = currentHashes.get(persistedEntry.getKey());
      if (currentHash != null && !currentHash.equals(persistedEntry.getValue())) {
        changed.add(persistedEntry.getKey());
      }
    }

    return new RuleHashDiff(
        Collections.unmodifiableSortedSet(added),
        Collections.unmodifiableSortedSet(removed),
        Collections.unmodifiableSortedSet(changed));
  }

  SortedSet<ConsistencyRuleId> dirtyIds(MigrationMode mode) {
    SortedSet<ConsistencyRuleId> dirty = new TreeSet<>(added);
    dirty.addAll(removed);
    if (mode == MigrationMode.HASH_DIFF) {
      dirty.addAll(changed);
    }

    return Collections.unmodifiableSortedSet(dirty);
  }
}
