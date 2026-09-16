package tools.vitruv.dsls.reactions.migration.migration;

import tools.vitruv.change.propagation.ConsistencyRuleId;
import tools.vitruv.change.propagation.ConsistencyRuleTriggerMatcher;

/** One dirty rule together with the matcher built from its trigger summary. */
record DirtyMatcher(ConsistencyRuleId id, ConsistencyRuleTriggerMatcher matcher) {}
