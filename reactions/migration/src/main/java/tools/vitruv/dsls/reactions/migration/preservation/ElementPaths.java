package tools.vitruv.dsls.reactions.migration.preservation;

import java.nio.file.Path;
import java.util.List;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import tools.vitruv.dsls.reactions.migration.adapter.ResourcePaths;

/**
 * Names a model element by a path that survives being written to a file and read back, so that
 * the same element can be found again in another state of the same model.
 */
public final class ElementPaths {
  private ElementPaths() {}

  /**
   * Returns the path of the given resource relative to the given folder.
   *
   * @param resource the resource to name, which may be {@code null}
   * @param folder the folder the path is to be relative to
   * @return the relative path, or {@code null} when there is no resource or it lies outside
   *     the folder
   */
  public static String resourceKey(Resource resource, Path folder) {
    return resource == null ? null : ResourcePaths.relativize(resource.getURI(), folder);
  }

  /**
   * Returns the path of the element inside its resource, made of the index of its root and the
   * fragment path from that root down to the element.
   *
   * @param element the element to name
   * @return the path of the element within its resource
   */
  public static String pathOf(EObject element) {
    EObject root = EcoreUtil.getRootContainer(element);
    return rootIndexOf(root) + "/" + EcoreUtil.getRelativeURIFragmentPath(root, element);
  }

  /**
   * Returns the element the given path names among the given roots.
   *
   * @param roots the roots of one resource, in the order the resource holds them
   * @param path a path as {@link #pathOf} writes it
   * @return the element, or {@code null} when the root index is out of range or the fragment
   *     path leads nowhere
   */
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
