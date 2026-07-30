package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tools.vitruv.dsls.reactions.migration.migration.LibraryCliFixture.assertVsumReloadsWithJarSpecs;
import static tools.vitruv.dsls.reactions.migration.migration.LibraryCliFixture.runCli;
import static tools.vitruv.dsls.reactions.migration.migration.LibraryCliFixture.writeMethodBody;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.dsls.reactions.migration.PropagationJars;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.vsum.PersistedRules;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;

@ExtendWith(RegisterMetamodelsInStandalone.class)
class PersistedFixtureCliMigrationTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();

  @TempDir Path tempDir;

  @BeforeAll
  static void prepareStandalone() {
    Vsums.prepareStandalone(ADAPTERS);
  }

  private static void assertLibraryModelsAreIntact(Path vsumFolder) throws Exception {
    assertTrue(
        Files.exists(vsumFolder.resolve("model").resolve("library.uml")),
        "the dominant UML model must exist");
    Map<String, String> uml = ModelStructure.ofUml(vsumFolder, ADAPTERS);
    Map<String, String> java = ModelStructure.ofJava(vsumFolder, ADAPTERS);
    for (String expected :
        List.of(
            LibraryExampleModel.IDENTIFIABLE,
            LibraryExampleModel.BORROWABLE,
            LibraryExampleModel.MEDIA_TYPE,
            LibraryExampleModel.ISBN,
            LibraryExampleModel.MEDIA,
            LibraryExampleModel.BOOK,
            LibraryExampleModel.LIBRARY_CARD,
            LibraryExampleModel.MEMBER)) {
      assertTrue(
          uml.containsKey(expected), "UML must contain " + expected + ", got " + uml.keySet());
      assertTrue(
          java.containsKey(expected),
          "the Java side must be regenerated for " + expected + ", got " + java.keySet());
    }
  }

  private static void assertMethodBodySurvived(Path vsumFolder) throws IOException {
    assertEquals(
        1,
        LibraryUserContent.statementCount(
            vsumFolder, ADAPTERS, LibraryExampleModel.BOOK, LibraryUserContent.BODIED_METHOD),
        "the hand-written method body must survive the migration");
    assertEquals(
        LibraryUserContent.COMPUTED_METHOD_STATEMENTS,
        LibraryUserContent.statementCount(
            vsumFolder, ADAPTERS, LibraryExampleModel.MEMBER, LibraryUserContent.COMPUTED_METHOD),
        "totalWeight is not a program without its body, so the migration must keep it");
  }

  private static Map<String, String> modelDigests(Path vsumFolder) throws Exception {
    Map<String, String> digests = new TreeMap<>();
    MessageDigest sha = MessageDigest.getInstance("SHA-256");
    for (String extension : List.of(".uml", ".java")) {
      for (Path file : ModelStructure.modelFiles(vsumFolder, extension)) {
        digests.put(
            vsumFolder.relativize(file).toString().replace('\\', '/'),
            java.util.HexFormat.of().formatHex(sha.digest(Files.readAllBytes(file))));
      }
    }

    return digests;
  }

  @Test
  @DisplayName("CLI migrates the committed persisted library VSUM in place")
  void test1() throws Exception {
    Path umljavaJar = PropagationJars.config1();
    Path vsumFolder = materializedFixture();
    writeMethodBody(vsumFolder);

    assertEquals(0, runCli(vsumFolder, umljavaJar, "--mode", "full", "--backup"));

    assertLibraryModelsAreIntact(vsumFolder);
    assertMethodBodySurvived(vsumFolder);
    assertVsumReloadsWithJarSpecs(vsumFolder, umljavaJar);

    assertEquals(
        0,
        runCli(vsumFolder, umljavaJar),
        "the migrated VSUM must still be migratable without changes");
    assertLibraryModelsAreIntact(vsumFolder);
    assertMethodBodySurvived(vsumFolder);
  }

  @Test
  @DisplayName("the default selective run finds the fixture's registry clean and changes nothing")
  void test2() throws Exception {
    Path umljavaJar = PropagationJars.config1();
    Path vsumFolder = materializedFixture();
    writeMethodBody(vsumFolder);
    assertFalse(
        PersistedRules.load(vsumFolder).hashes().isEmpty(),
        "the fixture must carry the registry the jar published");
    Map<String, String> before = modelDigests(vsumFolder);

    assertEquals(0, runCli(vsumFolder, umljavaJar));

    assertEquals(
        before, modelDigests(vsumFolder), "an unchanged rule set must repropagate nothing");
    assertMethodBodySurvived(vsumFolder);
  }

  private Path materializedFixture() throws Exception {
    return LibraryVsumFixtureGenerator.materializeInto(tempDir);
  }
}
