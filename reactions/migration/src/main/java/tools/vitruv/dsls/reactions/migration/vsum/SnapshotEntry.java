package tools.vitruv.dsls.reactions.migration.vsum;

import java.util.List;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;

/** The copied roots of one resource of a snapshot. */
record SnapshotEntry(URI uri, List<EObject> roots) {}
