package tools.vitruv.dsls.reactions.migration.preservation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.emf.common.util.URI;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;

/**
 * Finds the model files of a V-SUM folder, leaving out the metadata the framework keeps for
 * itself, the preservation report and the platform libraries a metamodel resolves against.
 */
public final class ModelFiles {
  private static final Set<String> METADATA_EXTENSIONS =
      Set.of("uuid", "models", "correspondence", "marker_vitruv");

  private ModelFiles() {}

  /**
   * Returns the model files below the given folder, sorted by their relative path so that two
   * states of the same V-SUM are walked in the same order.
   *
   * @param folder the folder of the V-SUM
   * @param adapters the metamodel adapters, which decide what counts as a platform library
   * @return the model files
   * @throws UncheckedIOException if the folder cannot be walked
   */
  public static List<Path> in(Path folder, AdapterRegistry adapters) {
    Path vsumMetadataFolder = folder.resolve("vsum");
    Path consistencyMetadataFolder = folder.resolve("consistencymetadata");
    try (Stream<Path> paths = Files.walk(folder)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(path -> !path.startsWith(vsumMetadataFolder))
          .filter(path -> !path.startsWith(consistencyMetadataFolder))
          .filter(path -> !METADATA_EXTENSIONS.contains(fileExtension(path)))
          .filter(path -> !PreservationReportWriter.FILE_NAME.equals(path.getFileName().toString()))
          .filter(
              path ->
                  !adapters.isPlatformLibraryResource(URI.createFileURI(path.toString()), folder))
          .sorted(Comparator.comparing(path -> relativeKey(folder, path)))
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String relativeKey(Path folder, Path path) {
    return folder.relativize(path).toString().replace('\\', '/');
  }

  private static String fileExtension(Path path) {
    String name = path.getFileName().toString();
    int dot = name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(dot + 1);
  }
}
