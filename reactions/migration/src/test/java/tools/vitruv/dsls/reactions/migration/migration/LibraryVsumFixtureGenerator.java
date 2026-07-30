package tools.vitruv.dsls.reactions.migration.migration;

import static tools.vitruv.dsls.reactions.migration.migration.LibraryCliFixture.permissiveInteraction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
import tools.vitruv.applications.umljava.JavaToUmlChangePropagationSpecification;
import tools.vitruv.applications.umljava.UmlToJavaChangePropagationSpecification;
import tools.vitruv.applications.util.temporary.java.JavaSetup;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

@Slf4j
public final class LibraryVsumFixtureGenerator {
  public static final String RESOURCE_PATH = "/persisted-library-vsum";
  public static final String DEFAULT_TARGET =
      "reactions/migration/src/test/resources/persisted-library-vsum";
  public static final String SENTINEL = "file:/__VSUM_ROOT__";
  public static final String STANDARD_LIBRARY_SENTINEL = "file:/__JAVA_BASE_JMOD__";

  private static final List<ChangePropagationSpecification> SPECIFICATIONS =
      List.of(
          new UmlToJavaChangePropagationSpecification(),
          new JavaToUmlChangePropagationSpecification());

  private LibraryVsumFixtureGenerator() {}

  public static void main(String[] args) throws Exception {
    Path target = Path.of(args.length > 0 ? args[0] : DEFAULT_TARGET);

    Vsums.prepareStandalone(new AdapterRegistry());
    JavaSetup.resetClasspathAndRegisterStandardLibrary();

    Path work = Files.createTempDirectory("library-fixture");
    InternalVirtualModel vsum =
        Vsums.build(
            work, SPECIFICATIONS, new TestUserInteraction.ResultProvider(permissiveInteraction()));
    try {
      LibraryExampleModel.seedInto(vsum, work);
      LibraryUserContent.writeTotalWeightBody(vsum);
    } finally {
      vsum.dispose();
    }

    deleteRecursively(target);
    copyTreeReplacing(
        work,
        target,
        Map.of(
            URI.createFileURI(work.toAbsolutePath().toString()).toString(), SENTINEL,
            standardLibraryUri(), STANDARD_LIBRARY_SENTINEL));
    deleteRecursively(work);

    log.info("Wrote persisted VSUM fixture to {}", target.toAbsolutePath());
  }

  public static Path materializeInto(Path parentFolder) throws Exception {
    Path fixture =
        Path.of(
            java.util.Objects.requireNonNull(
                    LibraryVsumFixtureGenerator.class.getResource(RESOURCE_PATH),
                    RESOURCE_PATH + " is not on the test classpath")
                .toURI());
    Path vsumFolder = parentFolder.resolve("library-vsum");
    copyTreeReplacing(
        fixture,
        vsumFolder,
        Map.of(
            SENTINEL, URI.createFileURI(vsumFolder.toAbsolutePath().toString()).toString(),
            STANDARD_LIBRARY_SENTINEL, standardLibraryUri()));
    return vsumFolder;
  }

  public static String standardLibraryUri() {
    Path javaBase =
        Path.of(System.getProperty("java.home"), "jmods", "java.base.jmod").toAbsolutePath();
    if (!Files.isRegularFile(javaBase)) {
      throw new IllegalStateException(
          "the fixture references the Java standard library, but this JVM has no "
              + javaBase
              + " - run the tests on a JDK, not a JRE");
    }

    return URI.createFileURI(javaBase.toString()).toString();
  }

  public static void copyTreeReplacing(Path source, Path target, Map<String, String> replacements)
      throws IOException {
    try (var walk = Files.walk(source)) {
      for (Path path : (Iterable<Path>) walk::iterator) {
        Path destination = target.resolve(source.relativize(path).toString());
        if (Files.isDirectory(path)) {
          Files.createDirectories(destination);
          continue;
        }
        Files.createDirectories(destination.getParent());
        Files.writeString(destination, replaceAll(Files.readString(path), replacements));
      }
    }
  }

  private static String replaceAll(String content, Map<String, String> replacements) {
    String replaced = content;
    for (Map.Entry<String, String> replacement : replacements.entrySet()) {
      replaced = replaced.replace(replacement.getKey(), replacement.getValue());
    }

    return replaced;
  }

  private static void deleteRecursively(Path folder) throws IOException {
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
