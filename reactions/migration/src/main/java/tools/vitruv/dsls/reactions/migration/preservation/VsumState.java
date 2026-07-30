package tools.vitruv.dsls.reactions.migration.preservation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import tools.vitruv.change.correspondence.Correspondence;
import tools.vitruv.change.correspondence.Correspondences;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.framework.vsum.helper.VsumFileSystemLayout;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class VsumState {
  private final Path folder;
  private final Map<String, List<EObject>> rootsByResourceKey;
  private final Map<EObject, Set<EObject>> partners;

  @Getter private final ResourceSet resourceSet;

  public static VsumState load(Path folder, AdapterRegistry adapters) {
    ResourceSet resourceSet = adapters.newResourceSet();
    Map<String, List<EObject>> rootsByResourceKey = loadModels(folder, adapters, resourceSet);
    return new VsumState(
        folder, rootsByResourceKey, loadCorrespondences(folder, resourceSet), resourceSet);
  }

  private static Map<String, List<EObject>> loadModels(
      Path folder, AdapterRegistry adapters, ResourceSet resourceSet) {
    Map<String, List<EObject>> rootsByResourceKey = new LinkedHashMap<>();
    for (Path file : ModelFiles.in(folder, adapters)) {
      Resource resource = adapters.load(resourceSet, URI.createFileURI(file.toString()));
      String key = ElementPaths.resourceKey(resource, folder);
      if (key != null) {
        rootsByResourceKey.put(key, new ArrayList<>(resource.getContents()));
      }
    }

    return rootsByResourceKey;
  }

  private static Map<EObject, Set<EObject>> loadCorrespondences(
      Path folder, ResourceSet resourceSet) {
    Map<EObject, Set<EObject>> partners = new LinkedHashMap<>();
    for (Correspondence correspondence : persistedCorrespondences(folder, resourceSet)) {
      List<EObject> left = resolved(correspondence.getLeftEObjects(), resourceSet);
      List<EObject> right = resolved(correspondence.getRightEObjects(), resourceSet);
      for (EObject leftElement : left) {
        for (EObject rightElement : right) {
          link(partners, leftElement, rightElement);
          link(partners, rightElement, leftElement);
        }
      }
    }

    return partners;
  }

  private static List<Correspondence> persistedCorrespondences(
      Path folder, ResourceSet resourceSet) {
    URI uri = correspondencesUri(folder);
    if (uri == null || !Files.exists(Path.of(uri.toFileString()))) {
      log.info("No persisted correspondences in {}; provenance is unknown there.", folder);
      return List.of();
    }

    Resource resource = resourceSet.getResource(uri, true);
    if (resource.getContents().isEmpty()
        || !(resource.getContents().getFirst() instanceof Correspondences correspondences)) {
      return List.of();
    }

    return List.copyOf(correspondences.getCorrespondences());
  }

  private static URI correspondencesUri(Path folder) {
    VsumFileSystemLayout layout = new VsumFileSystemLayout(folder);
    try {
      layout.prepare();
      return layout.getCorrespondencesURI();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static List<EObject> resolved(List<EObject> referenced, ResourceSet resourceSet) {
    List<EObject> elements = new ArrayList<>();
    for (EObject element : referenced) {
      EObject resolvedElement = EcoreUtil.resolve(element, resourceSet);
      if (resolvedElement.eIsProxy()) {
        log.debug("Skipping correspondence to unresolvable {}", EcoreUtil.getURI(element));
      } else {
        elements.add(resolvedElement);
      }
    }

    return elements;
  }

  private static void link(Map<EObject, Set<EObject>> partners, EObject from, EObject to) {
    partners.computeIfAbsent(from, key -> new LinkedHashSet<>()).add(to);
  }

  public Path folder() {
    return folder;
  }

  public Set<String> resourceKeys() {
    return rootsByResourceKey.keySet();
  }

  public List<EObject> rootsOf(String resourceKey) {
    return rootsByResourceKey.getOrDefault(resourceKey, List.of());
  }

  public boolean isCorresponded(EObject element) {
    return partners.containsKey(element);
  }

  public Collection<EObject> partnersOf(EObject element) {
    return partners.getOrDefault(element, Set.of());
  }
}
