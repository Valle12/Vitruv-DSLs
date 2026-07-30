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

public final class ModelSnapshot {
  private final List<SnapshotEntry> entries;

  private ModelSnapshot(List<SnapshotEntry> entries) {
    this.entries = List.copyOf(entries);
  }

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

  public List<EObject> allRoots() {
    return entries.stream().flatMap(entry -> entry.roots().stream()).toList();
  }

  public Map<URI, List<EObject>> rootsByUri() {
    Map<URI, List<EObject>> rootsByUri = new LinkedHashMap<>();
    for (SnapshotEntry entry : entries) {
      rootsByUri.computeIfAbsent(entry.uri(), key -> new ArrayList<>()).addAll(entry.roots());
    }

    return rootsByUri;
  }

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
