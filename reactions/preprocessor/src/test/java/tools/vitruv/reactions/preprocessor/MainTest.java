package tools.vitruv.reactions.preprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Objects;
import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MainTest {
  @Test
  @DisplayName("Test with two arguments")
  void test1() {
    String[] args = new String[] {"-c", "-r"};

    int exitCode = Main.run(args);

    assertEquals(1, exitCode);
  }

  @Test
  @DisplayName("Test with -h")
  void test2() {
    String[] args = new String[] {"-h"};

    int exitCode = Main.run(args);

    assertEquals(1, exitCode);
  }

  @Test
  @DisplayName("Test with --help")
  void test3() {
    String[] args = new String[] {"--help"};

    int exitCode = Main.run(args);

    assertEquals(0, exitCode);
  }

  @Test
  @DisplayName("Test successful run")
  @SneakyThrows
  void test4() {
    Path config =
        Path.of(
            Objects.requireNonNull(getClass().getClassLoader().getResource("configs/config5.json"))
                .toURI());
    Path reactions =
        Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("umljava")).toURI());
    String[] args = new String[] {"-c", config.toString(), "-r", reactions.toString()};

    int exitCode = Main.run(args);

    assertEquals(0, exitCode);
  }

  @Test
  @DisplayName("Test successful run with long parameters")
  @SneakyThrows
  void test5() {
    Path config =
        Path.of(
            Objects.requireNonNull(getClass().getClassLoader().getResource("configs/config5.json"))
                .toURI());
    Path reactions =
        Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("umljava")).toURI());
    String[] args =
        new String[] {"--config", config.toString(), "--reactions", reactions.toString()};

    int exitCode = Main.run(args);

    assertEquals(0, exitCode);
  }

  @Test
  @DisplayName("Test successful run with inverted parameter order")
  @SneakyThrows
  void test6() {
    Path config =
        Path.of(
            Objects.requireNonNull(getClass().getClassLoader().getResource("configs/config5.json"))
                .toURI());
    Path reactions =
        Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("umljava")).toURI());
    String[] args = new String[] {"--reactions", reactions.toString(), "-c", config.toString()};

    int exitCode = Main.run(args);

    assertEquals(0, exitCode);
  }
}
