package tools.vitruv.reactions.preprocessor.model;

/**
 * One annotated reactions file, split into its header and its code blocks.
 *
 * @param header everything the file declares before its first reaction or routine
 * @param codeBlocks the reactions and routines that follow the header
 */
public record ReactionsFile(Header header, CodeBlocks codeBlocks) {}
