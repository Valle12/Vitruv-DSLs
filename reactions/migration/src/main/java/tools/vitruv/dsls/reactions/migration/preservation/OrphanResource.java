package tools.vitruv.dsls.reactions.migration.preservation;

import java.util.List;
import org.eclipse.emf.ecore.EObject;

/**
 * A model file of the old state that has no counterpart after the migration, kept whole so
 * that what it held can still be reported.
 *
 * @param resourceKey the path of the file relative to the V-SUM folder
 * @param roots the roots the file held
 */
public record OrphanResource(String resourceKey, List<EObject> roots) {}
