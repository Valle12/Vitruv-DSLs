package tools.vitruv.dsls.reactions.migration.preservation;

import lombok.RequiredArgsConstructor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

@RequiredArgsConstructor
final class AnalysisTransplantTarget implements TransplantTarget {
  private final VsumState migratedState;

  @Override
  public EObject locate(EObject persistedElement) {
    return ElementPaths.resourceKey(persistedElement.eResource(), migratedState.folder()) == null
        ? null
        : persistedElement;
  }

  @Override
  public ResourceSet resourceSet() {
    return migratedState.getResourceSet();
  }

  @Override
  public Resource registerRoot(EObject root, URI uri) {
    Resource resource = migratedState.getResourceSet().createResource(uri);
    resource.getContents().add(root);
    return resource;
  }
}
