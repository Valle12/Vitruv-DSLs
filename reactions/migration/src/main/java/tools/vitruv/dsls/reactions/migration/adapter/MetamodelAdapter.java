package tools.vitruv.dsls.reactions.migration.adapter;

import java.util.Map;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

public interface MetamodelAdapter {
  boolean handles(String nsUri);

  default void prepareStandalone() {}

  default Map<Object, Object> loadOptions() {
    return Map.of();
  }

  default void prepareForSnapshot(EObject root) {}

  default void prepareForReplayInto(ResourceSet liveResourceSet, Resource detachedResource) {}

  default void normalizeLoadedResource(Resource resource) {}

  default boolean requiresChangeRecording() {
    return false;
  }

  default boolean isPlatformLibraryResource(URI uri) {
    return false;
  }
}
