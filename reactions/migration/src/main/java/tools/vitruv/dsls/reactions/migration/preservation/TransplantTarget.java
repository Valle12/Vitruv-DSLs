package tools.vitruv.dsls.reactions.migration.preservation;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

/**
 * Where the preservation step puts content back. A run that only reports writes into the
 * loaded models directly, whereas a run that changes the V-SUM goes through a view, so that
 * the framework records what was inserted.
 */
interface TransplantTarget {
  /**
   * Returns the element of this target that stands for the given element of the migrated files.
   *
   * @param persistedElement an element as it was read from the migrated folder
   * @return the element to change instead, or {@code null} when the target does not hold it
   */
  EObject locate(EObject persistedElement);

  /**
   * Returns the resource set that content to be inserted has to be loaded into.
   *
   * @return the resource set of this target
   */
  ResourceSet resourceSet();

  /**
   * Adds a root that the migrated state does not hold at all, such as the content of a model
   * file the migration left without a counterpart.
   *
   * @param root the root to add
   * @param uri the resource it is to live in
   * @return the resource it was added to
   */
  Resource registerRoot(EObject root, URI uri);
}
