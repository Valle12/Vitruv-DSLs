package tools.vitruv.dsls.reactions.migration.migration;

import java.util.List;
import org.eclipse.emf.common.util.URI;

record ResourceClassification(
    List<URI> snapshotUris, List<URI> derivedFileUris, List<URI> allModelFileUris) {}
