package tools.vitruv.reactions.preprocessor.model;

/**
 * Everything a reactions file declares before its first reaction or routine.
 *
 * @param reactionsName the name of the reactions segment, taken from the {@code reactions:} line
 * @param header the text up to the first code block, which a derived rule set keeps unchanged
 */
public record Header(String reactionsName, String header) {}
