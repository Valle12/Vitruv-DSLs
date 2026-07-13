package tools.vitruv.dsls.reactions.migration.adapter;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;

public class AdapterRegistry {
  private final List<MetamodelAdapter> adapters;
  private final MetamodelAdapter fallback;

  public AdapterRegistry() {
    this.adapters = List.of(new JavaModelAdapter());
    this.fallback = new DefaultModelAdapter();
  }

  public AdapterRegistry(List<MetamodelAdapter> adapters, MetamodelAdapter fallback) {
    this.adapters = List.copyOf(adapters);
    this.fallback = fallback;
  }

  public MetamodelAdapter adapterFor(String nsUri) {
    return adapters.stream().filter(adapter -> adapter.handles(nsUri)).findFirst().orElse(fallback);
  }

  public MetamodelAdapter adapterFor(EObject root) {
    return adapterFor(root.eClass().getEPackage().getNsURI());
  }

  public MetamodelAdapter adapterFor(Resource resource) {
    return resource.getContents().isEmpty() ? fallback : adapterFor(resource.getContents().get(0));
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

  public boolean isPlatformLibraryResource(URI uri) {
    return adapters.stream().anyMatch(adapter -> adapter.isPlatformLibraryResource(uri))
        || fallback.isPlatformLibraryResource(uri);
  }

  public boolean anyRootRequiresChangeRecording(Collection<EObject> roots) {
    return roots.stream().anyMatch(root -> adapterFor(root).requiresChangeRecording());
  }
}
