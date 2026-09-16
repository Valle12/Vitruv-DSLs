package tools.vitruv.dsls.reactions.migration.vsum;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import org.eclipse.emf.common.util.URI;

/** Copies a V-SUM folder elsewhere, rewriting the absolute paths its files carry. */
public final class VsumRelocation {
  private VsumRelocation() {}

  /**
   * Copies the V-SUM and replaces every mention of the old folder, both as a file URI and as a
   * plain path, with the new one, so that the copy refers to itself instead of to the original.
   *
   * @param from the folder to copy
   * @param to the folder to copy into
   * @throws UncheckedIOException if a file cannot be read, written or copied
   */
  public static void copy(Path from, Path to) {
    String oldRoot = rootUri(from);
    String newRoot = rootUri(to);
    String oldPath = from.toAbsolutePath().normalize().toString();
    String newPath = to.toAbsolutePath().normalize().toString();
    try (Stream<Path> paths = Files.walk(from)) {
      for (Path path : (Iterable<Path>) paths::iterator) {
        Path destination = to.resolve(from.relativize(path).toString());
        if (Files.isDirectory(path)) {
          Files.createDirectories(destination);
          continue;
        }

        Files.createDirectories(destination.getParent());
        byte[] bytes = Files.readAllBytes(path);
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.contains(oldRoot) || text.contains(oldPath)) {
          Files.writeString(destination, text.replace(oldRoot, newRoot).replace(oldPath, newPath));
        } else {
          Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Could not copy the VSUM from " + from + " to " + to, e);
    }
  }

  private static String rootUri(Path folder) {
    return URI.createFileURI(folder.toAbsolutePath().normalize().toString()).toString();
  }
}
