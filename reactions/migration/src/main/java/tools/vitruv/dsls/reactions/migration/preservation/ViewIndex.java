package tools.vitruv.dsls.reactions.migration.preservation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import tools.vitruv.framework.views.View;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class ViewIndex {
  private final Map<String, List<EObject>> rootsByResourceKey;
  private final Path folder;

  static ViewIndex of(View view, Path folder) {
    Map<String, List<EObject>> rootsByResourceKey = new LinkedHashMap<>();
    for (EObject root : view.getRootObjects()) {
      Resource resource = root.eResource();
      String resourceKey = ElementPaths.resourceKey(resource, folder);
      if (resourceKey != null) {
        rootsByResourceKey.putIfAbsent(resourceKey, new ArrayList<>(resource.getContents()));
      }
    }

    return new ViewIndex(rootsByResourceKey, folder);
  }

  EObject locate(EObject persistedElement) {
    String resourceKey = ElementPaths.resourceKey(persistedElement.eResource(), folder);
    if (resourceKey == null) {
      return null;
    }

    List<EObject> roots = rootsByResourceKey.get(resourceKey);
    return roots == null ? null : ElementPaths.locate(roots, ElementPaths.pathOf(persistedElement));
  }
}
