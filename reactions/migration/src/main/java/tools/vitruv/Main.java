package tools.vitruv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.plugin.EcorePlugin;
import tools.vitruv.applications.util.temporary.java.JavaSetup;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.cli.CLI;
import tools.vitruv.migration.*;
import tools.vitruv.setup.LayoutFreeJavaSetup;
import tools.vitruv.setup.SpecificationLoader;
import tools.vitruv.strategy.*;

@Slf4j
public class Main {
  public static void main(String[] args) {
    System.exit(run(args));
  }

  public static int run(String[] args) {
    CLI cli = new CLI(args);
    if (cli.isHelpCommand()) {
      cli.printUsage();
      return 0;
    }

    Optional<String> model = cli.getOptionValue(CLI.MODEL, CLI.MODEL_SHORT);
    if (model.isEmpty()) {
      log.error("Missing required option {}/{} <folder>", CLI.MODEL, CLI.MODEL_SHORT);
      cli.printUsage();
      return 1;
    }

    Path modelFolder = Path.of(model.get());
    if (!Files.isDirectory(modelFolder)) {
      log.error(
          "Model folder does not exist or is not a directory: {}", modelFolder.toAbsolutePath());
      return 1;
    }

    String strategy =
        cli.getOptionValue(CLI.STRATEGY, CLI.STRATEGY_SHORT).orElse(CLI.DEFAULT_STRATEGY);
    StrategyType strategyType;
    try {
      strategyType = StrategyType.valueOf(strategy);
    } catch (IllegalArgumentException e) {
      log.error(
          "Unknown strategy '{}'. Use one of: explicit, reachability, derivationLoss", strategy);
      cli.printUsage();
      return 1;
    }

    String dominantModel =
        cli.getOptionValue(CLI.DOMINANT, CLI.DOMINANT_SHORT).orElse(CLI.DEFAULT_DOMINANT);
    DominanceStrategy dominanceStrategy = getDominanceStrategyByName(strategyType, dominantModel);

    Optional<String> propagationsJar = cli.getOptionValue(CLI.PROPAGATIONS, CLI.PROPAGATIONS_SHORT);
    if (propagationsJar.isEmpty()) {
      log.error(
          "Missing required option {}/{} <jar> (the usecase jar with the change-propagation specifications)",
          CLI.PROPAGATIONS,
          CLI.PROPAGATIONS_SHORT);
      cli.printUsage();
      return 1;
    }

    if (cli.hasFlag(CLI.BACKUP, CLI.BACKUP_SHORT)) {
      Optional<Path> backup = backup(modelFolder);
      if (backup.isEmpty()) {
        return 1;
      }

      log.info("Backed up VSUM to {}", backup.get().toAbsolutePath());
    }

    EcorePlugin.ExtensionProcessor.process(null); // register standalone metamodels
    LayoutFreeJavaSetup.prepareFactories();
    JavaSetup.resetClasspathAndRegisterStandardLibrary();

    List<ChangePropagationSpecification> specifications;
    try {
      specifications = SpecificationLoader.loadFromJar(Path.of(propagationsJar.get()));
    } catch (IOException e) {
      log.error(
          "Failed to load change-propagation specifications from jar '{}': {}",
          propagationsJar.get(),
          e.getMessage());
      return 1;
    }

    PropagationGraph graph = new PropagationGraph(specifications);
    log.info("Propagation graph: {}", graph);

    log.info("Migrating VSUM at {}", modelFolder.toAbsolutePath());
    MigrationRunner migrationRunner = new MigrationRunner(specifications, graph, dominanceStrategy);
    try {
      migrationRunner.run(modelFolder);
      return 0;
    } catch (IOException e) {
      log.error("Migration failed for {}: {}", modelFolder.toAbsolutePath(), e.getMessage(), e);
      return 1;
    }
  }

  private static DominanceStrategy getDominanceStrategyByName(
      StrategyType strategy, String dominantModel) {
    return switch (strategy) {
      case EXPLICIT -> new ExplicitDominanceStrategy(dominantModel);
      case REACHABILITY -> new MaxReachabilityDominanceStrategy();
      case DERIVATION_LOSS -> new MinReDerivationLossDominanceStrategy();
    };
  }

  private static Optional<Path> backup(Path source) {
    String name = source.getFileName().toString();
    Path backup = source.resolveSibling("%s-backup-%d".formatted(name, System.currentTimeMillis()));
    try (Stream<Path> paths = Files.walk(source)) {
      for (Iterator<Path> it = paths.iterator(); it.hasNext(); ) {
        Path path = it.next();
        Path destination = backup.resolve(source.relativize(path).toString());
        if (Files.isDirectory(path)) {
          Files.createDirectories(destination);
        } else {
          Files.createDirectories(destination.getParent());
          Files.copy(path, destination);
        }
      }
    } catch (IOException e) {
      log.error("Failed to back up VSUM: {}", e.getMessage());
      return Optional.empty();
    }

    return Optional.of(backup);
  }
}
