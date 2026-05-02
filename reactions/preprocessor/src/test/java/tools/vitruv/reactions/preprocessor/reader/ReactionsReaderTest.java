package tools.vitruv.reactions.preprocessor.reader;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReactionsReaderTest {
  @Mock private MockedStatic<TextReader> textReaderMock;
  private ReactionsReader reactionsReader;

  @Test
  @DisplayName("Test invalid dir")
  void test1() {
    reactionsReader = new ReactionsReader("invalid/dir");

    Map<String, String> reactions = reactionsReader.readReactionsDir();

    assertTrue(reactions.isEmpty());
  }

  @Test
  @DisplayName("Test valid dir without subdirs")
  @SneakyThrows
  void test2() {
    Path path =
        Path.of(
            Objects.requireNonNull(getClass().getClassLoader().getResource("umljava/uml2java"))
                .toURI());
    reactionsReader = new ReactionsReader(path.toString());

    Map<String, String> reactions = reactionsReader.readReactionsDir();

    assertTrue(reactions.isEmpty());
  }

  @Test
  @DisplayName("Test valid dir with subdirs, but invalid file paths")
  @SneakyThrows
  void test3() {
    Path path =
        Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("umljava")).toURI());
    reactionsReader = new ReactionsReader(path.toString());
    textReaderMock
        .when(() -> TextReader.readTextFile(any(Path.class)))
        .thenReturn(Optional.empty());

    Map<String, String> reactions = reactionsReader.readReactionsDir();

    assertTrue(reactions.isEmpty());
  }

  @Test
  @DisplayName("Test valid dir with subdirs and valid file paths")
  @SneakyThrows
  void test4() {
    Path path =
        Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("umljava")).toURI());
    reactionsReader = new ReactionsReader(path.toString());
    textReaderMock.when(() -> TextReader.readTextFile(any(Path.class))).thenCallRealMethod();

    Map<String, String> reactions = reactionsReader.readReactionsDir();

    assertEquals(10, reactions.size());
    int java2umlCount = 0;
    int uml2javaCount = 0;
    for (Map.Entry<String, String> entry : reactions.entrySet()) {
      if (entry.getKey().startsWith("java2uml")) {
        java2umlCount++;
      } else if (entry.getKey().startsWith("uml2java")) {
        uml2javaCount++;
      }

      assertFalse(entry.getValue().isEmpty());
    }

    assertEquals(5, java2umlCount);
    assertEquals(5, uml2javaCount);
  }
}
