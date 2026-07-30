package tools.vitruv.dsls.reactions.migration.migration;

import static tools.vitruv.dsls.reactions.migration.migration.LibraryCliFixture.permissiveInteraction;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.dsls.reactions.migration.PropagationJars;
import tools.vitruv.dsls.reactions.migration.spec.JarSpecificationSource;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

@Slf4j
final class ConfigBaselines {
  private ConfigBaselines() {}

  static Path snapshotOf(Path snapshotsFolder, int config) {
    return snapshotsFolder.resolve("config" + config);
  }

  static Baseline writeSnapshot(int config, Path snapshotsFolder) throws Exception {
    Path work = Files.createTempDirectory("matrix-baseline-config" + config);
    boolean handWrittenContent;
    try {
      try (JarSpecificationSource specifications =
          JarSpecificationSource.fromJar(PropagationJars.config(config))) {
        InternalVirtualModel vsum =
            Vsums.build(
                work,
                specifications.createSpecifications(),
                new TestUserInteraction.ResultProvider(permissiveInteraction()));
        try {
          LibraryExampleModel.seedInto(vsum, work);
          handWrittenContent = LibraryUserContent.derivesTotalWeightBody(vsum);
          if (handWrittenContent) {
            LibraryUserContent.writeTotalWeightBody(vsum);
          } else {
            log.info(
                "config{} realizes the classifiers holding it as Java interfaces, so this baseline"
                    + " carries no hand-written method body.",
                config);
          }
        } finally {
          vsum.dispose();
        }
      }

      Path snapshot = snapshotOf(snapshotsFolder, config);
      ConfigMatrixFiles.deleteRecursively(snapshot);
      LibraryVsumFixtureGenerator.copyTreeReplacing(
          work,
          snapshot,
          Map.of(
              URI.createFileURI(work.toAbsolutePath().toString()).toString(),
                  LibraryVsumFixtureGenerator.SENTINEL,
              LibraryVsumFixtureGenerator.standardLibraryUri(),
                  LibraryVsumFixtureGenerator.STANDARD_LIBRARY_SENTINEL));
      return new Baseline(snapshot, handWrittenContent);
    } finally {
      ConfigMatrixFiles.deleteRecursively(work);
    }
  }

  static Path materialize(Path snapshot, Path parentFolder) throws Exception {
    if (!Files.isDirectory(snapshot)) {
      throw new IllegalStateException(
          "No baseline at "
              + snapshot.toAbsolutePath()
              + ". Derive it first - generate-config-matrix does that in its baseline step.");
    }

    Path vsumFolder = parentFolder.resolve("library-vsum");
    ConfigMatrixFiles.deleteRecursively(vsumFolder);
    LibraryVsumFixtureGenerator.copyTreeReplacing(
        snapshot,
        vsumFolder,
        Map.of(
            LibraryVsumFixtureGenerator.SENTINEL,
                URI.createFileURI(vsumFolder.toAbsolutePath().toString()).toString(),
            LibraryVsumFixtureGenerator.STANDARD_LIBRARY_SENTINEL,
                LibraryVsumFixtureGenerator.standardLibraryUri()));
    return vsumFolder;
  }

  record Baseline(Path snapshot, boolean handWrittenContent) {}
}
