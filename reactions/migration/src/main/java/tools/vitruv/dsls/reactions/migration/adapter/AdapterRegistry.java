package tools.vitruv.dsls.reactions.migration.adapter;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;

/**
 * Holds the metamodel adapters and answers with the one responsible for a namespace, a model
 * element or a resource, falling back to {@link DefaultModelAdapter} when none claims it.
 */
public class AdapterRegistry {
  private final List<MetamodelAdapter> adapters;
  private final MetamodelAdapter fallback;

  /** Creates a registry holding the adapters the migration ships with. */
  public AdapterRegistry() {
    this.adapters = List.of(new JavaModelAdapter());
    this.fallback = new DefaultModelAdapter();
  }

  /**
   * Returns the adapter responsible for the metamodel with the given namespace URI.
   *
   * @param nsUri the namespace URI of a metamodel, which may be {@code null}
   * @return the responsible adapter, the fallback when no adapter claims the namespace
   */
  public MetamodelAdapter adapterFor(String nsUri) {
    if (nsUri == null) {
      return fallback;
    }

    return adapters.stream().filter(adapter -> adapter.handles(nsUri)).findFirst().orElse(fallback);
  }

  /**
   * Returns the adapter responsible for the metamodel of the given element.
   *
   * @param root the element whose metamodel decides
   * @return the responsible adapter
   */
  public MetamodelAdapter adapterFor(EObject root) {
    return adapterFor(root.eClass().getEPackage().getNsURI());
  }

  /**
   * Returns the adapter responsible for the metamodel of the first root of the given resource.
   *
   * @param resource the resource whose content decides
   * @return the responsible adapter, the fallback when the resource holds no root
   */
  public MetamodelAdapter adapterFor(Resource resource) {
    return resource.getContents().isEmpty()
        ? fallback
        : adapterFor(resource.getContents().getFirst());
  }

  /** Prepares every adapter for use outside an Eclipse workbench. */
  public void prepareStandalone() {
    adapters.forEach(MetamodelAdapter::prepareStandalone);
    fallback.prepareStandalone();
  }

  /**
   * Returns the load options of every adapter merged into one map.
   *
   * @return the merged load options
   */
  public Map<Object, Object> combinedLoadOptions() {
    Map<Object, Object> options = new HashMap<>(fallback.loadOptions());
    adapters.forEach(adapter -> options.putAll(adapter.loadOptions()));
    return options;
  }

  /**
   * Creates a resource set that already carries the merged load options.
   *
   * @return the new resource set
   */
  public ResourceSet newResourceSet() {
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet.getLoadOptions().putAll(combinedLoadOptions());
    return resourceSet;
  }

  /**
   * Loads the resource at the given URI and lets its adapter repair what loading left incomplete.
   *
   * @param resourceSet the resource set to load into
   * @param uri the URI to load from
   * @return the loaded resource
   */
  public Resource load(ResourceSet resourceSet, URI uri) {
    Resource resource = resourceSet.getResource(uri, true);
    adapterFor(resource).normalizeLoadedResource(resource);
    return resource;
  }

  /**
   * Returns whether the resource at the given URI is a platform library rather than a model the
   * migration owns. A URI that names no file below the V-SUM folder counts as a library.
   *
   * @param uri the URI of a resource, which may be {@code null}
   * @param vsumFolder the folder of the V-SUM
   * @return whether the resource is a platform library
   */
  public boolean isPlatformLibraryResource(URI uri, Path vsumFolder) {
    if (uri == null) {
      return true;
    }

    String relativePath = ResourcePaths.relativize(uri, vsumFolder);
    return relativePath == null || adapterFor(uri).isPlatformLibraryResource(relativePath);
  }

  /**
   * Returns whether the resource at the given URI is a model the migration owns and may rewrite.
   *
   * @param uri the URI of a resource, which may be {@code null}
   * @param vsumFolder the folder of the V-SUM
   * @return whether the resource is a migrated model
   */
  public boolean isMigratedModel(URI uri, Path vsumFolder) {
    return uri != null && uri.isFile() && !isPlatformLibraryResource(uri, vsumFolder);
  }

  private MetamodelAdapter adapterFor(URI uri) {
    return adapters.stream()
        .filter(adapter -> adapter.claimsResource(uri))
        .findFirst()
        .orElse(fallback);
  }

  /**
   * Returns whether any of the given roots belongs to a metamodel that has to be built through
   * recorded changes.
   *
   * @param roots the roots to examine
   * @return whether change recording is required for at least one of them
   */
  public boolean anyRootRequiresChangeRecording(Collection<EObject> roots) {
    return roots.stream().anyMatch(root -> adapterFor(root).requiresChangeRecording());
  }

  /**
   * Returns the name that identifies the given element across two states of a model.
   *
   * @param element the element to identify
   * @return the identifying name, empty when its metamodel carries none
   */
  public Optional<String> externalIdentityOf(EObject element) {
    EPackage owner = element.eClass() == null ? null : element.eClass().getEPackage();
    if (owner == null) {
      return Optional.empty();
    }

    return adapterFor(owner.getNsURI()).externalIdentityOf(element);
  }
}
