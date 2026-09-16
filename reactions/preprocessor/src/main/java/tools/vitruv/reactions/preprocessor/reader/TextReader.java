package tools.vitruv.reactions.preprocessor.reader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/** Reads text files, reporting a failure as an empty result rather than as an exception. */
@Slf4j
public class TextReader {
  private TextReader() {}

  /**
   * Reads the given file as text.
   *
   * @param path the file to read
   * @return the content of the file, empty when it cannot be read
   */
  public static Optional<String> readTextFile(Path path) {
    try {
      return Optional.of(Files.readString(path));
    } catch (IOException e) {
      log.error("Error reading file: {}", path);
      return Optional.empty();
    }
  }
}
