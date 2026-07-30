package tools.vitruv.dsls.reactions.migration.preservation;

import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.adapter.ResourcePaths;

@RequiredArgsConstructor
final class ExternalTargets {
  private final AdapterRegistry adapters;
  private final Path oldFolder;
  private final Path newFolder;

  boolean isExternal(Object value) {
    return value instanceof EObject target
        && ElementPaths.resourceKey(target.eResource(), oldFolder) == null
        && ElementPaths.resourceKey(target.eResource(), newFolder) == null;
  }

  String identityOf(EObject target) {
    String known = adapters.externalIdentityOf(target).orElse(null);
    if (known != null) {
      return known;
    }

    if (target.eIsProxy()) {
      URI proxyUri = ((InternalEObject) target).eProxyURI();
      return proxyUri == null ? null : relativeToAMigratedFolder(proxyUri);
    }

    if (target.eResource() == null) {
      return null;
    }

    return relativeToAMigratedFolder(EcoreUtil.getURI(target));
  }

  private String relativeToAMigratedFolder(URI uri) {
    String fragment = uri.hasFragment() ? "#" + uri.fragment() : "";
    for (Path folder : new Path[] {oldFolder, newFolder}) {
      String key = ResourcePaths.relativize(uri, folder);
      if (key != null) {
        return key + fragment;
      }
    }

    return uri.toString();
  }
}
