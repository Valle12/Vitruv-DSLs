package tools.vitruv.dsls.reactions.migration.preservation;

import java.nio.file.Path;
import java.util.List;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import tools.vitruv.dsls.reactions.migration.adapter.ResourcePaths;

public final class ElementPaths {
  private ElementPaths() {}

  public static String resourceKey(Resource resource, Path folder) {
    return resource == null ? null : ResourcePaths.relativize(resource.getURI(), folder);
  }

  public static String pathOf(EObject element) {
    EObject root = EcoreUtil.getRootContainer(element);
    return rootIndexOf(root) + "/" + EcoreUtil.getRelativeURIFragmentPath(root, element);
  }

  public static EObject locate(List<EObject> roots, String path) {
    int separator = path.indexOf('/');
    int rootIndex = Integer.parseInt(path.substring(0, separator));
    if (rootIndex >= roots.size()) {
      return null;
    }

    EObject root = roots.get(rootIndex);
    String fragmentPath = path.substring(separator + 1);
    if (fragmentPath.isEmpty()) {
      return root;
    }

    try {
      return EcoreUtil.getEObject(root, fragmentPath);
    } catch (RuntimeException unresolvablePath) {
      return null;
    }
  }

  private static int rootIndexOf(EObject root) {
    Resource resource = root.eResource();
    return resource == null ? 0 : Math.max(0, resource.getContents().indexOf(root));
  }
}
