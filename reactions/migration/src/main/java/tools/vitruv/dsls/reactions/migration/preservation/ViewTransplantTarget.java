package tools.vitruv.dsls.reactions.migration.preservation;

import lombok.RequiredArgsConstructor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import tools.vitruv.framework.views.CommittableView;

/**
 * The target of a run that changes the V-SUM. It works through a view, so that what the
 * preservation step inserts is recorded as a change like any other.
 */
@RequiredArgsConstructor
final class ViewTransplantTarget implements TransplantTarget {
  private final CommittableView committable;
  private final ViewIndex viewIndex;

  @Override
  public EObject locate(EObject persistedElement) {
    return viewIndex.locate(persistedElement);
  }

  @Override
  public ResourceSet resourceSet() {
    for (EObject root : committable.getRootObjects()) {
      if (root.eResource() != null && root.eResource().getResourceSet() != null) {
        return root.eResource().getResourceSet();
      }
    }

    return null;
  }

  @Override
  @SuppressWarnings("UnstableApiUsage")
  public Resource registerRoot(EObject root, URI uri) {
    committable.registerRoot(root, uri);
    return root.eResource();
  }
}
