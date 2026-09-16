package tools.vitruv.reactions.preprocessor.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * The state the extraction keeps while it assembles one file of the derived rule set.
 *
 * @param reactionsFile the parsed source file the output is derived from
 * @param outputFile the file the assembled text is written to
 * @param sb the text assembled so far, beginning with the header of the source file
 * @param includedRoutines the names of the routines already appended, which keeps a routine
 *     reached more than once from being written twice
 * @param unqualifiedImports the reactions segments imported without a qualifying name, searched
 *     for a routine the source file itself does not declare
 */
public record ReactionsUtils(
    ReactionsFile reactionsFile,
    Path outputFile,
    StringBuilder sb,
    Set<String> includedRoutines,
    List<String> unqualifiedImports) {}
