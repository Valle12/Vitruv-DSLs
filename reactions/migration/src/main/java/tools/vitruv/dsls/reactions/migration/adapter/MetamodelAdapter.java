package tools.vitruv.dsls.reactions.migration.adapter;

import java.util.Map;
import java.util.Optional;
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

  default void dropResolutionCaches(EObject root) {}

  default void prepareForReplayInto(ResourceSet liveResourceSet, Iterable<EObject> detachedRoots) {}

  default void normalizeLoadedResource(Resource resource) {}

  default boolean refreshSerializedForm(Resource resource) {
    return false;
  }

  default boolean requiresChangeRecording() {
    return false;
  }

  default boolean claimsResource(URI uri) {
    return false;
  }

  default boolean isPlatformLibraryResource(String vsumRelativePath) {
    return false;
  }

  default Optional<String> externalIdentityOf(EObject element) {
    return Optional.empty();
  }
}
