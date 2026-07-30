package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.dsls.reactions.migration.PropagationJars;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;

@Slf4j
@ExtendWith(RegisterMetamodelsInStandalone.class)
class UmlJavaLibraryCompilationTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();

  @TempDir Path tempDir;

  @BeforeAll
  static void prepareStandalone() {
    Vsums.prepareStandalone(ADAPTERS);
  }

  @Test
  @DisplayName("the Java config1 derives from the library compiles")
  void test1() throws Exception {
    Path vsumFolder = LibraryVsumFixtureGenerator.materializeInto(tempDir);

    List<String> errors = compile(vsumFolder);

    assertTrue(errors.isEmpty(), "the derived Java must compile:\n" + String.join("\n", errors));
  }

  @Test
  @DisplayName("what the config7 rules alone derive compiles")
  void test2() throws Exception {
    Path vsumFolder = LibraryVsumFixtureGenerator.materializeInto(tempDir);
    assertEquals(
        0,
        LibraryCliFixture.runCli(
            vsumFolder,
            PropagationJars.config7(),
            "--mode",
            "full",
            "--source-update",
            "none",
            "--preserve",
            "off"));

    List<String> errors = compile(vsumFolder);
    assertTrue(
        errors.isEmpty(),
        "the config7 rules must derive Java that compiles:\n" + String.join("\n", errors));
  }

  @Test
  @DisplayName("preservation keeps the derived Java buildable but for one known static-method gap")
  void test3() throws Exception {
    Path vsumFolder = LibraryVsumFixtureGenerator.materializeInto(tempDir);
    assertEquals(
        0,
        LibraryCliFixture.runCli(
            vsumFolder, PropagationJars.config7(), "--mode", "full", "--source-update", "none"));

    List<String> errors = compile(vsumFolder);
    errors.forEach(error -> log.info("config7 with preservation: {}", error));
    assertEquals(
        1,
        errors.size(),
        "only the static-interface-method gap may remain; anything else is a regression:\n"
            + String.join("\n", errors));
    assertTrue(
        errors.getFirst().contains("Member.java"),
        "and it is the static `register` operation: " + errors.getFirst());
    assertTrue(
        ModelStructure.ofJava(vsumFolder, ADAPTERS).get("Media").contains("extends Borrowable"),
        "preservation is still carrying what the config7 rules alone cannot: the supertype config1"
            + " spelled as `implements` belongs on the interface config7 derives");
    assertTrue(
        ModelStructure.ofJava(vsumFolder, ADAPTERS).get("Book").contains("getIsbn"),
        "and the accessors survive the reshaping, which is the point of preservation here");
  }

  private List<String> compile(Path vsumFolder) throws IOException {
    assertFalse(
        ModelStructure.modelFiles(vsumFolder, ".java").isEmpty(),
        "there must be derived Java to compile");
    return JavaCompilation.errorsIn(vsumFolder, tempDir.resolve("compiled"));
  }
}
