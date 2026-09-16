package tools.vitruv.dsls.reactions.migration.vsum;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import lombok.extern.slf4j.Slf4j;

/**
 * A temporary folder tree for work that must not touch the V-SUM being migrated, such as a
 * trial migration. The tree is created on first use and deleted as a whole when the area is
 * closed.
 */
@Slf4j
public final class ScratchArea implements AutoCloseable {
  private Path root;
  private int folderCounter;

  private static void delete(Path path) {
    try {
      Files.delete(path);
    } catch (IOException e) {
      log.warn("Could not delete scratch file {}", path, e);
    }
  }

  /**
   * Creates a new empty folder inside the scratch area.
   *
   * @param prefix a name prefix, which a counter is appended to
   * @return the created folder
   */
  public Path newFolder(String prefix) {
    try {
      Path folder = rootFolder().resolve(prefix + "-" + folderCounter++);
      Files.createDirectories(folder);
      return folder;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void close() {
    if (root == null) {
      return;
    }

    try (var paths = Files.walk(root)) {
      paths.sorted(Comparator.reverseOrder()).forEach(ScratchArea::delete);
    } catch (IOException e) {
      log.warn("Failed to delete scratch folder {}", root, e);
    } finally {
      root = null;
    }
  }

  private Path rootFolder() throws IOException {
    if (root == null) {
      root = Files.createTempDirectory("vitruv-migration");
    }

    return root;
  }
}
