package tools.vitruv.dsls.reactions.migration.preservation;

/**
 * One value that was carried over.
 *
 * @param element what was carried over, described as a report names it
 * @param target the migrated element it now sits on
 * @param feature the feature of that element it was put into
 */
public record PreservedItem(String element, String target, String feature) {}
