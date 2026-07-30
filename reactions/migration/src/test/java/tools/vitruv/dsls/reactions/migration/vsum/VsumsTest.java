package tools.vitruv.dsls.reactions.migration.vsum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VsumsTest {
  private static final String ARCHIVE_ENTRY =
      "archive:file:/opt/jdk/lib/jrt-fs.jmod!/classes/java/lang/String.class";
  private static final String PATHMAP_ENTRY = "pathmap:/javaclass/java.lang.Object.java";
  private static final String FILE_ENTRY = "file:/vsum/model/library.uml";

  private static Path registryWith(Path folder, String... entries) throws Exception {
    Path modelsFile = folder.resolve("vsum").resolve("models.models");
    Files.createDirectories(modelsFile.getParent());
    Files.write(modelsFile, List.of(entries));
    return modelsFile;
  }

  @Test
  @DisplayName("entries that are not files of the VSUM are dropped")
  void test1(@TempDir Path tempDir) throws Exception {
    Path modelsFile = registryWith(tempDir, FILE_ENTRY, ARCHIVE_ENTRY, PATHMAP_ENTRY);

    Vsums.normalizeModelRegistry(tempDir);

    assertEquals(
        List.of(FILE_ENTRY),
        Files.readAllLines(modelsFile),
        "the archive entry embeds this machine's JDK path and cannot be reopened elsewhere");
  }

  @Test
  @DisplayName("pathmap entries go too, because the framework would try to delete them")
  void test2(@TempDir Path tempDir) throws Exception {
    Path modelsFile = registryWith(tempDir, FILE_ENTRY, PATHMAP_ENTRY);

    Vsums.normalizeModelRegistry(tempDir);

    assertEquals(
        List.of(FILE_ENTRY),
        Files.readAllLines(modelsFile),
        "a pathmap resource reloads, but every commit deletes the registered models that were"
            + " emptied, and EMF cannot delete a pathmap URI - it has no such protocol");
  }

  @Test
  @DisplayName("a folder that holds no VSUM is left exactly as it was")
  void test3(@TempDir Path tempDir) {
    Vsums.normalizeModelRegistry(tempDir);

    assertFalse(
        Files.exists(tempDir.resolve("vsum")),
        "normalizing must not scaffold a VSUM into a folder it was only handed");
    assertTrue(
        Files.exists(tempDir), "and it must not fail either - there is simply nothing to do");
  }
}
