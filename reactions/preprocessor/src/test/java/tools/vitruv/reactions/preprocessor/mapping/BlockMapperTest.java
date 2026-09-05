package tools.vitruv.reactions.preprocessor.mapping;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.vitruv.reactions.preprocessor.model.Header;
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

    Header header = result.header();
    assertTrue(header.header().isEmpty());
    assertTrue(header.reactionsName().isEmpty());
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

    Header header = result.header();
    assertTrue(header.header().contains("import umlToJavaAttribute using qualified names"));
    assertEquals("umlToJava", header.reactionsName());
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

    Header header = result.header();
    assertTrue(header.header().contains("reactions: umlToJavaClassifier"));
    assertEquals("umlToJavaClassifier", header.reactionsName());
    Map<String, List<String>> reactions = result.codeBlocks().reactions();
    assertTrue(reactions.size() > 1);
    List<String> umlClassToJavaClassReactions = reactions.get("umlClassToJavaClass");
    assertFalse(umlClassToJavaClassReactions.isEmpty());
    assertTrue(
        umlClassToJavaClassReactions
            .getFirst()
            .contains("after element uml::Class inserted in uml::Package[packagedElement]"));
    Map<String, String> routines = result.codeBlocks().routines();
    assertTrue(routines.size() > 1);
    assertTrue(
        routines
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

    Header header = result.header();
    assertTrue(header.header().contains("reactions: umlToJavaClassifier"));
    assertEquals("umlToJavaClassifier", header.reactionsName());
    Map<String, List<String>> reactions = result.codeBlocks().reactions();
    assertEquals(1, reactions.size());
    List<String> umlClassToJavaClassReactions = reactions.get("umlClassToJavaClass");
    assertFalse(umlClassToJavaClassReactions.isEmpty());
    assertTrue(
        umlClassToJavaClassReactions
            .getFirst()
            .contains("after element uml::Class inserted in uml::Package[packagedElement]"));
    Map<String, String> routines = result.codeBlocks().routines();
    assertEquals(1, routines.size());
    assertTrue(
        routines
            .get("createOrFindJavaClass")
            .contains("require absence of java::Class corresponding to umlClassifier"));
  }
}
