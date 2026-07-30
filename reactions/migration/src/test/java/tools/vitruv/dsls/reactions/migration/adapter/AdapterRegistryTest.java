package tools.vitruv.dsls.reactions.migration.adapter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.eclipse.emf.common.util.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdapterRegistryTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();

  private static URI uriOf(Path folder, String relativePath) {
    return URI.createFileURI(folder.resolve(relativePath).toString());
  }

  @Test
  @DisplayName("the JDK stubs JaMoPP materialises under the VSUM are platform libraries")
  void test1(@TempDir Path tempDir) {
    Path vsumFolder = tempDir.resolve("library-vsum");
    assertTrue(
        ADAPTERS.isPlatformLibraryResource(
            uriOf(vsumFolder, "src/java/lang/Object.java"), vsumFolder));
    assertTrue(
        ADAPTERS.isPlatformLibraryResource(
            uriOf(vsumFolder, "src/java/package-info.java"), vsumFolder));
    assertTrue(
        ADAPTERS.isPlatformLibraryResource(
            uriOf(vsumFolder, "src/javax/xml/Parser.java"), vsumFolder));
  }

  @Test
  @DisplayName("the VSUM's own models are not platform libraries")
  void test2(@TempDir Path tempDir) {
    Path vsumFolder = tempDir.resolve("library-vsum");
    assertFalse(
        ADAPTERS.isPlatformLibraryResource(uriOf(vsumFolder, "src/catalog/Book.java"), vsumFolder));
    assertFalse(
        ADAPTERS.isPlatformLibraryResource(uriOf(vsumFolder, "model/library.uml"), vsumFolder));
    assertTrue(ADAPTERS.isMigratedModel(uriOf(vsumFolder, "src/catalog/Book.java"), vsumFolder));
    assertTrue(ADAPTERS.isMigratedModel(uriOf(vsumFolder, "model/library.uml"), vsumFolder));
  }

  @Test
  @DisplayName("a VSUM checked out below a folder called java keeps all of its models")
  void test3(@TempDir Path tempDir) {
    Path vsumFolder = tempDir.resolve("java").resolve("library-vsum");
    assertFalse(
        ADAPTERS.isPlatformLibraryResource(uriOf(vsumFolder, "src/catalog/Book.java"), vsumFolder),
        "only the path below the VSUM folder may decide this");
    assertTrue(ADAPTERS.isMigratedModel(uriOf(vsumFolder, "src/catalog/Book.java"), vsumFolder));
    assertTrue(ADAPTERS.isMigratedModel(uriOf(vsumFolder, "model/library.uml"), vsumFolder));
    assertTrue(
        ADAPTERS.isPlatformLibraryResource(
            uriOf(vsumFolder, "src/java/lang/Object.java"), vsumFolder),
        "the stubs below it are still recognised");
  }

  @Test
  @DisplayName("the Java heuristic does not vote on another metamodel's models")
  void test4(@TempDir Path tempDir) {
    Path vsumFolder = tempDir.resolve("pcm-vsum");
    assertFalse(
        ADAPTERS.isPlatformLibraryResource(
            uriOf(vsumFolder, "java/default.repository"), vsumFolder),
        "a PCM model in a folder called java is not a JDK stub");
    assertTrue(ADAPTERS.isMigratedModel(uriOf(vsumFolder, "java/default.repository"), vsumFolder));
  }

  @Test
  @DisplayName("anything that is not a file model of this VSUM is left alone")
  void test5(@TempDir Path tempDir) {
    Path vsumFolder = tempDir.resolve("library-vsum");
    assertFalse(ADAPTERS.isMigratedModel(null, vsumFolder));
    assertFalse(
        ADAPTERS.isMigratedModel(URI.createURI("cache:/javaclass/java.lang.String"), vsumFolder));
    assertFalse(
        ADAPTERS.isMigratedModel(uriOf(tempDir, "elsewhere/model.uml"), vsumFolder),
        "a file outside the VSUM is none of its models");
  }
}
