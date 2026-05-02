package tools.vitruv.reactions.preprocessor.reader;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TextReaderTest {
  @Test
  @DisplayName("Test invalid path")
  void test1() {
    Path path = Path.of("test.reactions");

    Optional<String> optionalResult = TextReader.readTextFile(path);

    assertTrue(optionalResult.isEmpty());
  }

  @Test
  @DisplayName("Test valid path")
  @SneakyThrows
  void test2() {
    Path path =
        Path.of(
            Objects.requireNonNull(
                    getClass().getClassLoader().getResource("umljava/uml2java/UmlToJava.reactions"))
                .toURI());

    Optional<String> optionalResult = TextReader.readTextFile(path);

    assertTrue(optionalResult.isPresent());
    String result = optionalResult.get();
    assertTrue(result.contains("umlToJavaAttribute"));
    assertTrue(result.contains("umlToJavaClassifier"));
    assertTrue(result.contains("umlToJavaMethod"));
  }
}
