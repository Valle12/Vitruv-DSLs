package tools.vitruv.reactions.preprocessor.model;

import java.util.List;
import java.util.Map;

public record CodeBlocks(Map<String, List<String>> reactions, Map<String, String> routines) {}
