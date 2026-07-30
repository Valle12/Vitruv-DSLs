package tools.vitruv.dsls.reactions.migration.adapter;

import java.nio.file.Path;
import org.eclipse.emf.common.util.URI;

public final class ResourcePaths {
  private ResourcePaths() {}

  public static String relativize(URI uri, Path folder) {
    if (uri == null || !uri.isFile() || uri.toFileString() == null) {
      return null;
    }

    Path file = Path.of(uri.toFileString()).toAbsolutePath().normalize();
    Path base = folder.toAbsolutePath().normalize();
    return file.startsWith(base) ? base.relativize(file).toString().replace('\\', '/') : null;
  }
}
