package tools.vitruv.dsls.reactions.migration.migration;

import java.util.List;
import org.eclipse.emf.common.util.URI;

/**
 * The resources of a V-SUM as a full migration sees them, namely the ones it copies into the
 * snapshot, the files of the derived models and every model file it knows of.
 */
record ResourceClassification(
    List<URI> snapshotUris, List<URI> derivedFileUris, List<URI> allModelFileUris) {}
