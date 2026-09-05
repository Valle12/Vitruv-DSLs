package tools.vitruv.reactions.preprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
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

  @Test
  @DisplayName("Test run with top-level files and shared routine imports")
  @SneakyThrows
  void test7() {
    Path config =
        Path.of(
            Objects.requireNonNull(getClass().getClassLoader().getResource("mixedConfig.json"))
                .toURI());
    Path reactions =
        Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("mixed")).toURI());
    String[] args = new String[] {"-c", config.toString(), "-r", reactions.toString()};

    int exitCode = Main.run(args);

    assertEquals(0, exitCode);

    Path outputDir = config.getParent().resolve("mixedConfig-reactions");

    String aggregateContent = Files.readString(outputDir.resolve("Aggregate.reactions"));
    assertEquals(Files.readString(reactions.resolve("Aggregate.reactions")), aggregateContent);

    String callerContent = Files.readString(outputDir.resolve("alpha/Caller.reactions"));
    assertTrue(callerContent.contains("reaction SomethingInserted"));
    assertTrue(callerContent.contains("routine localOnly("));
    assertFalse(callerContent.contains("@feature"));
    assertFalse(callerContent.contains("routine helperOne("));

    String helpersContent = Files.readString(outputDir.resolve("alpha/Helpers.reactions"));
    assertTrue(helpersContent.contains("routine helperOne("));
    assertTrue(helpersContent.contains("routine helperTwo("));
    assertEquals(
        helpersContent.indexOf("routine helperOne("),
        helpersContent.lastIndexOf("routine helperOne("));
    assertEquals(
        helpersContent.indexOf("routine helperTwo("),
        helpersContent.lastIndexOf("routine helperTwo("));
    assertFalse(helpersContent.contains("unusedHelper"));
    assertFalse(helpersContent.contains("localOnly"));

    String qualifiedContent = Files.readString(outputDir.resolve("beta/Qualified.reactions"));
    assertTrue(qualifiedContent.contains("reaction OtherInserted"));
    assertFalse(qualifiedContent.contains("routine helperTwo("));
  }
}
