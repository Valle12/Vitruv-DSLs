package tools.vitruv.dsls.reactions.migration.migration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import tools.vitruv.change.atomic.hid.ObjectResolutionUtil;
import tools.vitruv.change.correspondence.Correspondence;
import tools.vitruv.change.correspondence.view.CorrespondenceModelView;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

@Slf4j
final class NotRepropagated {
  private static final int SAMPLE_SIZE = 10;

  private final Set<String> keys;

  private NotRepropagated(Set<String> keys) {
    this.keys = keys;
  }

  static NotRepropagated of(
      InternalVirtualModel vsum, Set<URI> sourceUris, Path vsumFolder, AdapterRegistry adapters) {
    CorrespondenceModelView<Correspondence> correspondences = vsum.getCorrespondenceModel();
    Set<String> keys = new LinkedHashSet<>();
    List<String> samples = new ArrayList<>();
    for (Resource resource : List.copyOf(vsum.getViewSourceModels())) {
      if (resource.getURI() != null && sourceUris.contains(resource.getURI())) {
        collectFrom(resource, correspondences, vsumFolder, adapters, keys, samples);
      }
    }

    if (!keys.isEmpty()) {
      log.info("Leaving {} element(s) out of the repropagation, e.g. {}", keys.size(), samples);
    }

    return new NotRepropagated(keys);
  }

  private static void collectFrom(
      Resource resource,
      CorrespondenceModelView<Correspondence> correspondences,
      Path vsumFolder,
      AdapterRegistry adapters,
      Set<String> keys,
      List<String> samples) {
    for (Iterator<EObject> it = resource.getAllContents(); it.hasNext(); ) {
      EObject element = it.next();
      if (element.eIsProxy()) {
        continue;
      }

      String reason = reasonToLeaveOut(correspondences, element, vsumFolder, adapters);
      if (reason != null) {
        String key = keyOf(element);
        keys.add(key);
        if (samples.size() < SAMPLE_SIZE) {
          samples.add(key + " (" + reason + ")");
        }
      }
    }
  }

  static String keyOf(EObject element) {
    Resource resource = element.eResource();
    return (resource == null ? "" : resource.getURI())
        + "#"
        + ObjectResolutionUtil.getHierarchicUriFragment(element);
  }

  private static String reasonToLeaveOut(
      CorrespondenceModelView<Correspondence> correspondences,
      EObject element,
      Path vsumFolder,
      AdapterRegistry adapters) {
    Set<EObject> partners = correspondences.getCorrespondingEObjects(element);
    if (partners.isEmpty()) {
      return "nothing corresponds to it, so tearing it down can only disturb what references it";
    }

    for (EObject partner : partners) {
      if (partner != null
          && !(partner instanceof EClass)
          && isOutsideTheModels(partner, vsumFolder, adapters)) {
        return "it corresponds to " + EcoreUtil.getURI(partner) + ", outside the models";
      }
    }

    return null;
  }

  private static boolean isOutsideTheModels(
      EObject partner, Path vsumFolder, AdapterRegistry adapters) {
    URI uri =
        partner.eIsProxy() ? ((InternalEObject) partner).eProxyURI() : EcoreUtil.getURI(partner);
    return !adapters.isMigratedModel(uri, vsumFolder);
  }

  Predicate<EObject> asExclusion() {
    return element -> keys.contains(keyOf(element));
  }
}
