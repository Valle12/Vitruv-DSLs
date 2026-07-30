package tools.vitruv.dsls.reactions.migration.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class ConfigMatrixFiles {
  private ConfigMatrixFiles() {}

  static void copyFile(Path source, Path target) throws IOException {
    Files.createDirectories(target.getParent());
    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
  }

  static void copyTree(Path source, Path target) throws IOException {
    if (!Files.isDirectory(source)) {
      throw new IllegalArgumentException("not a folder: " + source.toAbsolutePath());
    }

    try (var walk = Files.walk(source)) {
      for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile).sorted()::iterator) {
        copyFile(file, target.resolve(source.relativize(file).toString()));
      }
    }
  }

  static void deleteRecursively(Path folder) throws IOException {
    if (!Files.exists(folder)) {
      return;
    }

    try (var walk = Files.walk(folder)) {
      List<Path> paths = new ArrayList<>(walk.sorted(Comparator.reverseOrder()).toList());
      for (Path path : paths) {
        Files.deleteIfExists(path);
      }
    }
  }
}
