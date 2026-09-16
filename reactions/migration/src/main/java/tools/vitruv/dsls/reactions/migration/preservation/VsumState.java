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
import java.util.Objects;
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

/**
 * One state of a V-SUM read straight from its folder, with the models keyed by file and the
 * correspondences between their elements resolved. Reading from the folder rather than from a
 * running V-SUM is what lets the state from before a migration be compared with the one after.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class VsumState {
  private final Path folder;
  private final Map<String, List<EObject>> rootsByResourceKey;
  private final Map<EObject, Set<Partner>> partners;

  @Getter private final ResourceSet resourceSet;

  /**
   * Reads the models and the correspondences of the V-SUM in the given folder.
   *
   * @param folder the folder of the persisted V-SUM
   * @param adapters the metamodel adapters
   * @return the state the folder holds
   */
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

  private static Map<EObject, Set<Partner>> loadCorrespondences(
      Path folder, ResourceSet resourceSet) {
    Map<EObject, Set<Partner>> partners = new LinkedHashMap<>();
    for (Correspondence correspondence : persistedCorrespondences(folder, resourceSet)) {
      List<EObject> left = resolved(correspondence.getLeftEObjects(), resourceSet);
      List<EObject> right = resolved(correspondence.getRightEObjects(), resourceSet);
      String tag = Objects.requireNonNullElse(correspondence.getTag(), "");
      for (EObject leftElement : left) {
        for (EObject rightElement : right) {
          link(partners, leftElement, rightElement, tag);
          link(partners, rightElement, leftElement, tag);
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

  private static void link(
      Map<EObject, Set<Partner>> partners, EObject from, EObject to, String tag) {
    partners.computeIfAbsent(from, key -> new LinkedHashSet<>()).add(new Partner(to, tag));
  }

  /**
   * Returns where this state was read from.
   *
   * @return the folder of the V-SUM
   */
  public Path folder() {
    return folder;
  }

  /**
   * Returns the model files of this state, each as a path relative to the folder.
   *
   * @return the keys under which the models are held
   */
  public Set<String> resourceKeys() {
    return rootsByResourceKey.keySet();
  }

  /**
   * Returns the roots the given model file holds.
   *
   * @param resourceKey the path of the file relative to the folder
   * @return its roots, empty when this state holds no such file
   */
  public List<EObject> rootsOf(String resourceKey) {
    return rootsByResourceKey.getOrDefault(resourceKey, List.of());
  }

  /**
   * Returns whether a consistency rule produced or is answerable for the given element.
   *
   * @param element the element to look up
   * @return whether a correspondence names it
   */
  public boolean isCorresponded(EObject element) {
    return partners.containsKey(element);
  }

  /**
   * Returns the elements the given one corresponds to in the other models.
   *
   * @param element the element to look up
   * @return its partners, empty when no correspondence names it
   */
  public Collection<Partner> partnersOf(EObject element) {
    return partners.getOrDefault(element, Set.of());
  }

  /**
   * The other side of one correspondence, with the tag the rule recorded it under.
   *
   * @param element the element on the other side of the correspondence
   * @param tag the tag the consistency rule recorded it under
   */
  public record Partner(EObject element, String tag) {}
}
