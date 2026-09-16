package tools.vitruv.reactions.preprocessor.reader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/** Reads the annotated reactions of a directory and of its immediate subdirectories. */
@Slf4j
public class ReactionsReader {
  private final Path dir;

  /**
   * Creates a reader for the given directory.
   *
   * @param dir the path of the directory holding the annotated reactions
   */
  public ReactionsReader(String dir) {
    this.dir = Path.of(dir);
  }

  /**
   * Reads every reactions file of the directory and of the directories directly below it.
   *
   * @return the text of each file, keyed by its file name and, for a file in a subdirectory, by
   *     that subdirectory and the file name, empty when the directory cannot be read
   */
  public Map<String, String> readReactionsDir() {
    Map<String, String> reactions = new HashMap<>();

    try (Stream<Path> paths = Files.list(dir)) {
      paths.forEach(
          path -> {
            if (Files.isDirectory(path)) {
              reactions.putAll(readReactionsFiles(path));
              return;
            }

            TextReader.readTextFile(path)
                .ifPresent(content -> reactions.put(path.getFileName().toString(), content));
          });
    } catch (IOException e) {
      log.error("Error reading reactions directory");
    }

    return reactions;
  }

  private Map<String, String> readReactionsFiles(Path dir) {
    Map<String, String> reactions = new HashMap<>();

    try (Stream<Path> paths = Files.list(dir)) {
      paths.forEach(
          path -> {
            Optional<String> optionalContent = TextReader.readTextFile(path);
            if (optionalContent.isEmpty()) {
              return;
            }

            String content = optionalContent.get();
            String relativeDir = path.getName(path.getNameCount() - 2).toString();
            String fileName = path.getFileName().toString();
            reactions.put(relativeDir + "/" + fileName, content);
          });
    } catch (IOException e) {
      log.error("Error reading reactions file");
    }

    return reactions;
  }
}
