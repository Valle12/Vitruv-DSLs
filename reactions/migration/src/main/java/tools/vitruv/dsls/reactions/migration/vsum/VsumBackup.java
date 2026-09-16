package tools.vitruv.dsls.reactions.migration.vsum;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * A copy of a V-SUM folder taken before the migration touches it, so that a run which fails
 * part way through can be undone.
 */
@Slf4j
public final class VsumBackup {
  private final Path sourceFolder;
  private final Path backupFolder;

  private VsumBackup(Path sourceFolder, Path backupFolder) {
    this.sourceFolder = sourceFolder;
    this.backupFolder = backupFolder;
  }

  /**
   * Copies the given folder to a sibling folder named after it and the current time.
   *
   * @param folder the V-SUM folder to back up
   * @return the backup that was taken
   * @throws IOException if the folder cannot be copied
   */
  public static VsumBackup create(Path folder) throws IOException {
    String name = folder.getFileName().toString();
    Path backupFolder =
        folder.resolveSibling("%s-backup-%d".formatted(name, System.currentTimeMillis()));
    copyTree(folder, backupFolder);
    log.info("Backed up VSUM to {}", backupFolder.toAbsolutePath());
    return new VsumBackup(folder, backupFolder);
  }

  /**
   * Copies a folder tree, overwriting the files the target folder already holds.
   *
   * @param source the folder to copy
   * @param target the folder to copy into
   * @throws IOException if a file cannot be read or written
   */
  public static void copyTree(Path source, Path target) throws IOException {
    try (Stream<Path> paths = Files.walk(source)) {
      for (Path path : (Iterable<Path>) paths::iterator) {
        Path destination = target.resolve(source.relativize(path).toString());
        if (Files.isDirectory(path)) {
          Files.createDirectories(destination);
        } else {
          Files.createDirectories(destination.getParent());
          Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  private static void clearFolder(Path folder) throws IOException {
    if (!Files.exists(folder)) {
      Files.createDirectories(folder);
      return;
    }

    try (Stream<Path> paths = Files.walk(folder)) {
      paths
          .sorted(Comparator.reverseOrder())
          .filter(path -> !path.equals(folder))
          .forEach(VsumBackup::deleteLoudly);
    }
  }

  private static void deleteLoudly(Path path) {
    try {
      Files.delete(path);
    } catch (IOException e) {
      log.warn("Could not delete {} while clearing the folder for restore", path, e);
    }
  }

  /**
   * Returns where the backup was written.
   *
   * @return the backup folder
   */
  public Path location() {
    return backupFolder;
  }

  /**
   * Empties the folder the backup was taken from and copies the backup back into it.
   *
   * @throws IOException if the folder cannot be cleared or the backup cannot be copied
   */
  public void restore() throws IOException {
    clearFolder(sourceFolder);
    copyTree(backupFolder, sourceFolder);
    log.info("Restored VSUM from backup {}", backupFolder.toAbsolutePath());
  }
}
