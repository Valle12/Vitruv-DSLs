package tools.vitruv.dsls.reactions.migration.vsum;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import org.eclipse.emf.common.util.URI;

public final class VsumRelocation {
  private VsumRelocation() {}

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
