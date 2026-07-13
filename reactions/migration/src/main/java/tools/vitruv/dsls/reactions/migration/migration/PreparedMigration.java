package tools.vitruv.dsls.reactions.migration.migration;

import java.nio.file.Path;
import java.util.List;
import org.eclipse.emf.common.util.URI;
import tools.vitruv.dsls.reactions.migration.graph.DominancePlan;
import tools.vitruv.dsls.reactions.migration.vsum.ModelSnapshot;

record PreparedMigration(
    DominancePlan plan,
    ModelSnapshot sourceSnapshot,
    Path derivedOriginalsFolder,
    List<URI> modelFilesToDelete) {}
