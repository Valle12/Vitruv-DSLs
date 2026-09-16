package tools.vitruv.reactions.preprocessor.model;

import java.util.List;
import java.util.Map;

/**
 * The reactions and routines of one reactions file, kept as text so that they can be copied
 * verbatim into a derived rule set.
 *
 * @param reactions the reactions of each feature, keyed by the feature name the {@code @feature}
 *     annotation carries, in the order they appear in the file
 * @param routines the text of each routine, keyed by the routine name
 */
public record CodeBlocks(Map<String, List<String>> reactions, Map<String, String> routines) {}
