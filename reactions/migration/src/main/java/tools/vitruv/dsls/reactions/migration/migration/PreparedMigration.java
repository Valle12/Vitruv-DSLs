package tools.vitruv.dsls.reactions.migration.migration;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.eclipse.emf.common.util.URI;
import tools.vitruv.dsls.reactions.migration.graph.DominancePlan;
import tools.vitruv.dsls.reactions.migration.vsum.ModelSnapshot;

record PreparedMigration(
    DominancePlan plan,
    SourceSelection selection,
    Optional<Path> reusableTrial,
    ModelSnapshot sourceSnapshot,
    Path preMigrationFolder,
    List<Path> oldDerivedFiles,
    List<URI> modelFilesToDelete) {}
