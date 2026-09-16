package tools.vitruv.dsls.reactions.migration.adapter;

import java.nio.file.Path;
import org.eclipse.emf.common.util.URI;

/** Expresses the URI of a resource as a path relative to the V-SUM folder. */
public final class ResourcePaths {
  private ResourcePaths() {}

  /**
   * Returns the given URI as a path below the given folder, written with forward slashes so that
   * it reads the same on every operating system.
   *
   * @param uri the URI of a resource
   * @param folder the folder the path is to be relative to
   * @return the relative path, or {@code null} when the URI names no file or lies outside the
   *     folder
   */
  public static String relativize(URI uri, Path folder) {
    if (uri == null || !uri.isFile() || uri.toFileString() == null) {
      return null;
    }

    Path file = Path.of(uri.toFileString()).toAbsolutePath().normalize();
    Path base = folder.toAbsolutePath().normalize();
    return file.startsWith(base) ? base.relativize(file).toString().replace('\\', '/') : null;
  }
}
