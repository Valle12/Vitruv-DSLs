package tools.vitruv.reactions.preprocessor.mapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.vitruv.reactions.preprocessor.model.BlockIndex;
import tools.vitruv.reactions.preprocessor.model.CodeBlocks;
import tools.vitruv.reactions.preprocessor.model.ReactionsFile;

public class BlockMapper {
  private static final char OPEN_CURLY = '{';
  private static final char CLOSE_CURLY = '}';
  private static final Pattern REACTION_PATTERN =
      Pattern.compile("@feature[^\\n]*type\\s*=\\s*\"(\\w+)\"[^\\n]*\\s*(reaction)\\s*\\w+\\s*\\{");
  private static final Pattern ROUTINE_PATTERN =
      Pattern.compile("(routine)\\s+(\\w+)\\s*\\([^)]*\\)\\s*\\{");

  public ReactionsFile extractBlocks(String content) {
    String header = extractHeader(content);
    CodeBlocks codeBlocks = extractCodeBlocks(content.substring(header.length()));
    return new ReactionsFile(header, codeBlocks);
  }

  private String extractHeader(String content) {
    int contentBegin = Integer.MAX_VALUE;

    Pattern feature = Pattern.compile("@feature\\(type\\s*=\\s*\"\\w+\"\\)");
    Matcher featureMatcher = feature.matcher(content);
    if (featureMatcher.find()) {
      contentBegin = featureMatcher.start();
    }

    Pattern reaction = Pattern.compile("reaction\\s*\\w*\\s*\\{");
    Matcher reactionMatcher = reaction.matcher(content);
    if (reactionMatcher.find()) {
      contentBegin = Math.min(contentBegin, reactionMatcher.start());
    }

    Pattern routine = Pattern.compile("routine\\s*\\w*\\s*\\(");
    Matcher routineMatcher = routine.matcher(content);
    if (routineMatcher.find()) {
      contentBegin = Math.min(contentBegin, routineMatcher.start());
    }

    if (contentBegin == Integer.MAX_VALUE) {
      return content;
    }

    return content.substring(0, contentBegin);
  }

  private CodeBlocks extractCodeBlocks(String content) {
    Matcher reactionMatcher = REACTION_PATTERN.matcher(content);
    Matcher routineMatcher = ROUTINE_PATTERN.matcher(content);

    Map<String, List<String>> reactions = new HashMap<>();
    Map<String, String> routines = new HashMap<>();

    boolean hasReaction = reactionMatcher.find();
    boolean hasRoutine = routineMatcher.find();

    while (hasReaction || hasRoutine) {
      boolean reactionFound =
          hasReaction && (!hasRoutine || reactionMatcher.start() < routineMatcher.start());
      Matcher matcher = reactionFound ? reactionMatcher : routineMatcher;
      BlockIndex blockIndex = extractBlockIndices(matcher, reactionFound, content);
      int start = blockIndex.start();
      int end = blockIndex.end();

      if (reactionFound) {
        reactions
            .computeIfAbsent(matcher.group(1), k -> new ArrayList<>())
            .add(content.substring(start, end));
        hasReaction = reactionMatcher.find();
      } else {
        routines.put(matcher.group(2), content.substring(start, end));
        hasRoutine = routineMatcher.find();
      }
    }

    return new CodeBlocks(reactions, routines);
  }

  private BlockIndex extractBlockIndices(Matcher matcher, boolean reactionFound, String content) {
    int start = matcher.start(reactionFound ? 2 : 1);
    int index = content.indexOf(OPEN_CURLY, start);
    int openCurlyPair = 0;

    do {
      if (content.charAt(index) == OPEN_CURLY) {
        openCurlyPair++;
      } else if (content.charAt(index) == CLOSE_CURLY) {
        openCurlyPair--;
      }

      index++;
    } while (openCurlyPair > 0);

    return new BlockIndex(start, index);
  }
}
