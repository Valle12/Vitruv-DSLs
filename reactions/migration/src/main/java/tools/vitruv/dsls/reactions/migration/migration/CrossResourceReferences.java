package tools.vitruv.dsls.reactions.migration.migration;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;

public final class CrossResourceReferences {
  private CrossResourceReferences() {}

  public static DetachedReferences detach(Map<URI, List<EObject>> rootsByUri) {
    Map<EObject, URI> ownerUri = ownerUris(rootsByUri);
    List<Reference> detached = new ArrayList<>();
    for (Map.Entry<URI, List<EObject>> entry : rootsByUri.entrySet()) {
      for (EObject root : entry.getValue()) {
        detachFrom(root, entry.getKey(), ownerUri, detached);
        root.eAllContents()
            .forEachRemaining(object -> detachFrom(object, entry.getKey(), ownerUri, detached));
      }
    }

    return new DetachedReferences(detached);
  }

  private static Map<EObject, URI> ownerUris(Map<URI, List<EObject>> rootsByUri) {
    Map<EObject, URI> ownerUri = new IdentityHashMap<>();
    for (Map.Entry<URI, List<EObject>> entry : rootsByUri.entrySet()) {
      for (EObject root : entry.getValue()) {
        ownerUri.put(root, entry.getKey());
        root.eAllContents().forEachRemaining(object -> ownerUri.put(object, entry.getKey()));
      }
    }

    return ownerUri;
  }

  @SuppressWarnings("unchecked")
  private static void detachFrom(
      EObject holder, URI ownerUri, Map<EObject, URI> uriOf, List<Reference> detached) {
    for (EReference feature : holder.eClass().getEAllReferences()) {
      if (feature.isContainment()
          || feature.isContainer()
          || !feature.isChangeable()
          || feature.isDerived()) {
        continue;
      }

      if (feature.isMany()) {
        List<EObject> values = (List<EObject>) holder.eGet(feature);
        for (EObject target : List.copyOf(values)) {
          if (isInOtherResource(target, ownerUri, uriOf)) {
            detached.add(new Reference(holder, feature, target));
            values.remove(target);
          }
        }
      } else if (holder.eGet(feature) instanceof EObject target
          && isInOtherResource(target, ownerUri, uriOf)) {
        detached.add(new Reference(holder, feature, target));
        holder.eUnset(feature);
      }
    }
  }

  private static boolean isInOtherResource(EObject target, URI ownerUri, Map<EObject, URI> uriOf) {
    URI targetUri = uriOf.get(target);
    return targetUri != null && !targetUri.equals(ownerUri);
  }
}