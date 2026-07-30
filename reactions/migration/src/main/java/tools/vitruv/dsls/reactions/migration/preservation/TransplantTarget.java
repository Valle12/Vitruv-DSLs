package tools.vitruv.dsls.reactions.migration.preservation;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

interface TransplantTarget {
  EObject locate(EObject persistedElement);

  ResourceSet resourceSet();

  Resource registerRoot(EObject root, URI uri);
}
