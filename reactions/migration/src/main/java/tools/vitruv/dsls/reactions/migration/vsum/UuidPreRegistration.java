package tools.vitruv.dsls.reactions.migration.vsum;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import tools.vitruv.change.atomic.uuid.UuidResolver;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

public final class UuidPreRegistration {
  private UuidPreRegistration() {}

  public static int registerAllLiveElements(
      InternalVirtualModel vsum, AdapterRegistry adapters, Path vsumFolder) {
    for (Resource resource : coveredResources(vsum, adapters, vsumFolder)) {
      EcoreUtil.resolveAll(resource);
    }

    UuidResolver resolver = vsum.getUuidResolver();
    int registered = 0;
    for (Resource resource : coveredResources(vsum, adapters, vsumFolder)) {
      for (Iterator<EObject> it = resource.getAllContents(); it.hasNext(); ) {
        EObject element = it.next();
        if (!element.eIsProxy() && !resolver.hasUuid(element)) {
          resolver.registerEObject(element);
          registered++;
        }
      }
    }

    return registered;
  }

  private static List<Resource> coveredResources(
      InternalVirtualModel vsum, AdapterRegistry adapters, Path vsumFolder) {
    return List.copyOf(vsum.getViewSourceModels()).stream()
        .filter(resource -> adapters.isMigratedModel(resource.getURI(), vsumFolder))
        .toList();
  }
}
