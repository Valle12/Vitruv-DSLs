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

public class AdapterRegistry {
  private final List<MetamodelAdapter> adapters;
  private final MetamodelAdapter fallback;

  public AdapterRegistry() {
    this.adapters = List.of(new JavaModelAdapter());
    this.fallback = new DefaultModelAdapter();
  }

  public MetamodelAdapter adapterFor(String nsUri) {
    if (nsUri == null) {
      return fallback;
    }

    return adapters.stream().filter(adapter -> adapter.handles(nsUri)).findFirst().orElse(fallback);
  }

  public MetamodelAdapter adapterFor(EObject root) {
    return adapterFor(root.eClass().getEPackage().getNsURI());
  }

  public MetamodelAdapter adapterFor(Resource resource) {
    return resource.getContents().isEmpty()
        ? fallback
        : adapterFor(resource.getContents().getFirst());
  }

  public void prepareStandalone() {
    adapters.forEach(MetamodelAdapter::prepareStandalone);
    fallback.prepareStandalone();
  }

  public Map<Object, Object> combinedLoadOptions() {
    Map<Object, Object> options = new HashMap<>(fallback.loadOptions());
    adapters.forEach(adapter -> options.putAll(adapter.loadOptions()));
    return options;
  }

  public ResourceSet newResourceSet() {
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet.getLoadOptions().putAll(combinedLoadOptions());
    return resourceSet;
  }

  public Resource load(ResourceSet resourceSet, URI uri) {
    Resource resource = resourceSet.getResource(uri, true);
    adapterFor(resource).normalizeLoadedResource(resource);
    return resource;
  }

  public boolean isPlatformLibraryResource(URI uri, Path vsumFolder) {
    if (uri == null) {
      return true;
    }

    String relativePath = ResourcePaths.relativize(uri, vsumFolder);
    return relativePath == null || adapterFor(uri).isPlatformLibraryResource(relativePath);
  }

  public boolean isMigratedModel(URI uri, Path vsumFolder) {
    return uri != null && uri.isFile() && !isPlatformLibraryResource(uri, vsumFolder);
  }

  private MetamodelAdapter adapterFor(URI uri) {
    return adapters.stream()
        .filter(adapter -> adapter.claimsResource(uri))
        .findFirst()
        .orElse(fallback);
  }

  public boolean anyRootRequiresChangeRecording(Collection<EObject> roots) {
    return roots.stream().anyMatch(root -> adapterFor(root).requiresChangeRecording());
  }

  public Optional<String> externalIdentityOf(EObject element) {
    EPackage owner = element.eClass() == null ? null : element.eClass().getEPackage();
    if (owner == null) {
      return Optional.empty();
    }

    return adapterFor(owner.getNsURI()).externalIdentityOf(element);
  }
}
