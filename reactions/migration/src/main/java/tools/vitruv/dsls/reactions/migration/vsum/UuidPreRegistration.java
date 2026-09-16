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

/** Gives every element of a V-SUM an identifier before a selective migration begins. */
public final class UuidPreRegistration {
  private UuidPreRegistration() {}

  /**
   * Resolves every proxy of the migrated models and registers each element that carries no
   * identifier yet, so that a later teardown and re-insertion can name elements the V-SUM has
   * never propagated.
   *
   * @param vsum the V-SUM to register in
   * @param adapters the metamodel adapters
   * @param vsumFolder the folder of the V-SUM
   * @return how many elements were newly registered
   */
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
