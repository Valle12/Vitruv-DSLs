package tools.vitruv.dsls.reactions.migration.adapter;

import java.util.Map;
import java.util.Optional;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

/**
 * Adapts the migration to the peculiarities of one metamodel. Every hook carries a default that
 * suits a plain EMF metamodel, so an adapter states only where its own metamodel deviates, and
 * {@link DefaultModelAdapter} accepts every namespace as the fallback.
 */
public interface MetamodelAdapter {
  /**
   * Returns whether this adapter is responsible for the metamodel with the given namespace URI.
   *
   * @param nsUri the namespace URI of a metamodel
   * @return whether this adapter is responsible for it
   */
  boolean handles(String nsUri);

  /**
   * Sets up what the metamodel needs to be usable outside an Eclipse workbench, such as resource
   * factories or a class path. Called once, before the first model is loaded.
   */
  default void prepareStandalone() {}

  /**
   * Returns the load options every resource set of the migration is given.
   *
   * @return the load options of this metamodel, none by default
   */
  default Map<Object, Object> loadOptions() {
    return Map.of();
  }

  /**
   * Clears the caches the metamodel keeps below the given root, so that a later lookup resolves
   * against the current state rather than against the state the cache was filled from.
   *
   * @param root the model root whose caches are to be cleared
   */
  default void dropResolutionCaches(EObject root) {}

  /**
   * Rebinds detached roots to a live resource set before they are inserted into it, so that the
   * references they still hold into the state they were detached from point at live equivalents.
   *
   * @param liveResourceSet the resource set the roots are about to enter
   * @param detachedRoots the roots to rebind
   */
  default void prepareForReplayInto(ResourceSet liveResourceSet, Iterable<EObject> detachedRoots) {}

  /**
   * Repairs what loading leaves incomplete in a resource of this metamodel.
   *
   * @param resource the resource that has just been loaded
   */
  default void normalizeLoadedResource(Resource resource) {}

  /**
   * Writes the file of the given resource back from its model, which a metamodel whose files are
   * source text rather than XMI has to do for the file to match the migrated model.
   *
   * @param resource the resource to write back
   * @return whether the file on disk changed, false by default
   */
  default boolean refreshSerializedForm(Resource resource) {
    return false;
  }

  /**
   * Returns whether a model of this metamodel has to be built through recorded changes instead of
   * being attached to the V-SUM directly.
   *
   * @return whether change recording is required, false by default
   */
  default boolean requiresChangeRecording() {
    return false;
  }

  /**
   * Returns whether the resource at the given URI belongs to this metamodel. The answer follows
   * from the URI alone, so it is available before the resource has been loaded.
   *
   * @param uri the URI of a resource
   * @return whether the resource belongs to this metamodel, false by default
   */
  default boolean claimsResource(URI uri) {
    return false;
  }

  /**
   * Returns whether the given path holds a platform library rather than a model the migration
   * owns, such as the standard library a Java model resolves its types against.
   *
   * @param vsumRelativePath the path of a resource relative to the V-SUM folder
   * @return whether the resource is a platform library, false by default
   */
  default boolean isPlatformLibraryResource(String vsumRelativePath) {
    return false;
  }

  /**
   * Returns a name that identifies the given element across two states of a model, for a
   * metamodel that carries such a name.
   *
   * @param element the element to identify
   * @return the identifying name, empty when the metamodel has none for this element
   */
  default Optional<String> externalIdentityOf(EObject element) {
    return Optional.empty();
  }
}
