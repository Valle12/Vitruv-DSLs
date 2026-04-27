package tools.vitruv.reactions.preprocessor.reader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReactionsReader {
  private final Path dir;

  public ReactionsReader(String dir) {
    this.dir = Path.of(dir);
  }

  public Map<String, String> readReactionsDir() {
    Map<String, String> reactions = new HashMap<>();

    try (Stream<Path> paths = Files.list(dir)) {
      paths.forEach(path -> reactions.putAll(readReactionsFiles(path)));
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
