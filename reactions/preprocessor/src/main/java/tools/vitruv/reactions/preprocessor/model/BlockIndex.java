package tools.vitruv.reactions.preprocessor.model;

/**
 * The character range one reaction or routine occupies in the text of a reactions file.
 *
 * @param start the offset of the first character of the block
 * @param end the offset one past the closing brace of the block
 */
public record BlockIndex(int start, int end) {}
