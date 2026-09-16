package tools.vitruv.dsls.reactions.migration.vsum;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;

/**
 * A detached copy of the models a V-SUM holds, taken before a full migration empties it and
 * inserted again afterwards. The copy keeps no tie to the resource set it was read from, so it
 * outlives the teardown of the V-SUM.
 */
public final class ModelSnapshot {
  private final List<SnapshotEntry> entries;

  private ModelSnapshot(List<SnapshotEntry> entries) {
    this.entries = List.copyOf(entries);
  }

  /**
   * Reads the given resources and copies their roots out of the V-SUM. A resource holding no
   * migrated model, such as a platform library, is passed over.
   *
   * @param resourceUris the resources to copy, of which duplicates are read once
   * @param adapters the metamodel adapters
   * @param vsumFolder the folder of the V-SUM
   * @return the detached copy
   */
  public static ModelSnapshot of(
      Collection<URI> resourceUris, AdapterRegistry adapters, Path vsumFolder) {
    ResourceSet loadSet = adapters.newResourceSet();

    List<URI> perRootUris = new ArrayList<>();
    List<EObject> allRoots = new ArrayList<>();
    for (URI uri : new LinkedHashSet<>(resourceUris)) {
      if (!adapters.isMigratedModel(uri, vsumFolder)) {
        continue;
      }

      Resource resource = loadSet.getResource(uri, true);
      for (EObject root : resource.getContents()) {
        adapters.adapterFor(root).dropResolutionCaches(root);
        perRootUris.add(uri);
        allRoots.add(root);
      }
    }

    return groupedByUri(perRootUris, copiesOf(allRoots));
  }

  private static URI rebase(URI fileUri, Path originalRoot, Path targetFolder) {
    Path file = Path.of(fileUri.toFileString()).toAbsolutePath().normalize();
    Path relative = originalRoot.relativize(file);
    return URI.createFileURI(targetFolder.resolve(relative).toString());
  }

  private static List<EObject> copiesOf(List<EObject> roots) {
    return new ArrayList<>(EcoreUtil.copyAll(roots));
  }

  private static ModelSnapshot groupedByUri(List<URI> perRootUris, List<EObject> copiedRoots) {
    Map<URI, List<EObject>> grouped = new LinkedHashMap<>();
    for (int i = 0; i < copiedRoots.size(); i++) {
      grouped.computeIfAbsent(perRootUris.get(i), key -> new ArrayList<>()).add(copiedRoots.get(i));
    }

    return new ModelSnapshot(
        grouped.entrySet().stream()
            .map(entry -> new SnapshotEntry(entry.getKey(), entry.getValue()))
            .toList());
  }

  /**
   * Returns every copied root, across all resources.
   *
   * @return the copied roots
   */
  public List<EObject> allRoots() {
    return entries.stream().flatMap(entry -> entry.roots().stream()).toList();
  }

  /**
   * Returns the copied roots grouped by the resource they were read from.
   *
   * @return the copied roots per resource URI
   */
  public Map<URI, List<EObject>> rootsByUri() {
    Map<URI, List<EObject>> rootsByUri = new LinkedHashMap<>();
    for (SnapshotEntry entry : entries) {
      rootsByUri.computeIfAbsent(entry.uri(), key -> new ArrayList<>()).addAll(entry.roots());
    }

    return rootsByUri;
  }

  /**
   * Returns the same copy with every resource URI moved into another folder, so that it can be
   * inserted into a V-SUM somewhere else, such as the one a trial migration works in.
   *
   * @param targetFolder the folder the URIs are to point into
   * @param originalFolder the folder they point into now
   * @return the relocated copy
   */
  public ModelSnapshot relocatedTo(Path targetFolder, Path originalFolder) {
    Path originalRoot = originalFolder.toAbsolutePath().normalize();
    List<SnapshotEntry> relocated =
        entries.stream()
            .map(
                entry ->
                    new SnapshotEntry(
                        rebase(entry.uri(), originalRoot, targetFolder), entry.roots()))
            .toList();
    return new ModelSnapshot(relocated);
  }
}
