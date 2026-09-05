package tools.vitruv.dsls.reactions.migration.migration;

import tools.vitruv.change.propagation.ConsistencyRuleId;
import tools.vitruv.change.propagation.ConsistencyRuleTriggerMatcher;

record DirtyMatcher(ConsistencyRuleId id, ConsistencyRuleTriggerMatcher matcher) {}
