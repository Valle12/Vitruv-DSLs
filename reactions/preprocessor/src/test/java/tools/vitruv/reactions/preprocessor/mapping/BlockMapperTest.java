package tools.vitruv.reactions.preprocessor.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.vitruv.reactions.preprocessor.model.ReactionsFile;
import tools.vitruv.reactions.preprocessor.reader.TextReader;

class BlockMapperTest {
  private final BlockMapper blockMapper = new BlockMapper();

  @Test
  @DisplayName("Test with no content")
  @SneakyThrows
  void test1() {
    Path path =
        Path.of(
            Objects.requireNonNull(getClass().getClassLoader().getResource("empty.reactions"))
                .toURI());
    Optional<String> optionalContent = TextReader.readTextFile(path);
    String content = optionalContent.orElseThrow();

    ReactionsFile result = blockMapper.extractBlocks(content);

    assertTrue(result.header().isEmpty());
    assertTrue(result.codeBlocks().reactions().isEmpty());
    assertTrue(result.codeBlocks().routines().isEmpty());
  }

  @Test
  @DisplayName("Test with only header")
  @SneakyThrows
  void test2() {
    Path path =
        Path.of(
            Objects.requireNonNull(
                    getClass().getClassLoader().getResource("umljava/uml2java/UmlToJava.reactions"))
                .toURI());
    Optional<String> optionalContent = TextReader.readTextFile(path);
    String content = optionalContent.orElseThrow();

    ReactionsFile result = blockMapper.extractBlocks(content);

    assertTrue(result.header().contains("import umlToJavaAttribute using qualified names"));
    assertTrue(result.codeBlocks().reactions().isEmpty());
    assertTrue(result.codeBlocks().routines().isEmpty());
  }

  @Test
  @DisplayName("Test with feature as first occurence")
  @SneakyThrows
  void test3() {
    Path path =
        Path.of(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("umljava/uml2java/UmlToJavaClassifier.reactions"))
                .toURI());
    Optional<String> optionalContent = TextReader.readTextFile(path);
    String content = optionalContent.orElseThrow();

    ReactionsFile result = blockMapper.extractBlocks(content);

    assertTrue(result.header().contains("reactions: umlToJavaClassifier"));
    assertTrue(result.codeBlocks().reactions().size() > 1);
    Map<String, String> reaction = result.codeBlocks().reactions();
    assertTrue(
        reaction
            .get("umlClassToJavaClass")
            .contains("after element uml::Class inserted in uml::Package[packagedElement]"));
    assertTrue(result.codeBlocks().routines().size() > 1);
    Map<String, String> routine = result.codeBlocks().routines();
    assertTrue(
        routine
            .get("createOrFindJavaClass")
            .contains("require absence of java::Class corresponding to umlClassifier"));
  }

  @Test
  @DisplayName("Test with routine as first occurence")
  @SneakyThrows
  void test4() {
    Path path =
        Path.of(
            Objects.requireNonNull(
                    getClass().getClassLoader().getResource("routineFirst.reactions"))
                .toURI());
    Optional<String> optionalContent = TextReader.readTextFile(path);
    String content = optionalContent.orElseThrow();

    ReactionsFile result = blockMapper.extractBlocks(content);

    assertTrue(result.header().contains("reactions: umlToJavaClassifier"));
    assertEquals(1, result.codeBlocks().reactions().size());
    Map<String, String> reaction = result.codeBlocks().reactions();
    assertTrue(
        reaction
            .get("umlClassToJavaClass")
            .contains("after element uml::Class inserted in uml::Package[packagedElement]"));
    assertEquals(1, result.codeBlocks().routines().size());
    Map<String, String> routine = result.codeBlocks().routines();
    assertTrue(
        routine
            .get("createOrFindJavaClass")
            .contains("require absence of java::Class corresponding to umlClassifier"));
  }
}
