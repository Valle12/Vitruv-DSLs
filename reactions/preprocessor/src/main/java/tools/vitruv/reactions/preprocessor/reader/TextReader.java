package tools.vitruv.reactions.preprocessor.reader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TextReader {
  private TextReader() {}

  public static Optional<String> readTextFile(Path path) {
    try {
      return Optional.of(Files.readString(path));
    } catch (IOException e) {
      log.error("Error reading file: {}", path);
      return Optional.empty();
    }
  }
}
