package tools.vitruv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import tools.vitruv.applications.umljava.JavaToUmlChangePropagationSpecification;
import tools.vitruv.applications.umljava.UmlToJavaChangePropagationSpecification;
import tools.vitruv.applications.util.temporary.java.JavaSetup;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.migration.DominanceStrategy;
import tools.vitruv.migration.MigrationRunner;
import tools.vitruv.migration.MinReDerivationLossDominanceStrategy;
import tools.vitruv.migration.PropagationGraph;

/** Load a persistent VSUM, select one of the models, propagate to other ones */
@Slf4j
public class Main {
  private static final String MODEL_FOLDER = "model";
  private static final String MIGRATION_FOLDER = "vitruv-migration";

  public static void main(String[] args) throws IOException {
    JavaSetup.prepareFactories();
    JavaSetup.resetClasspathAndRegisterStandardLibrary();

    Path sourceFolder = Path.of(MODEL_FOLDER);
    // TODO should be pointed at usecase with reactions to load the necessary propagations
    List<ChangePropagationSpecification> specifications =
        List.of(
            new UmlToJavaChangePropagationSpecification(),
            new JavaToUmlChangePropagationSpecification());
    PropagationGraph graph = PropagationGraph.fromSpecifications(specifications);
    log.info("{}", graph);
    DominanceStrategy dominanceStrategy = new MinReDerivationLossDominanceStrategy();
    Path scratchFolder = Files.createTempDirectory(MIGRATION_FOLDER);
    MigrationRunner runner =
        new MigrationRunner(specifications, graph, dominanceStrategy, scratchFolder);
    runner.run(sourceFolder);
  }
}
