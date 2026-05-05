package tools.vitruv.reactions.preprocessor.model;

import java.nio.file.Path;
import java.util.Set;

public record ReactionsUtils(
    ReactionsFile reactionsFile, Path outputFile, StringBuilder sb, Set<String> includedRoutines) {}
