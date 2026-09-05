package tools.vitruv.dsls.reactions.migration.migration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;
import tools.vitruv.dsls.reactions.migration.preservation.ModelFiles;

final class ModelStates {
  private ModelStates() {}

  static List<Resource> load(Path folder, AdapterRegistry adapters) {
    ResourceSet resourceSet = adapters.newResourceSet();
    return ModelFiles.in(folder, adapters).stream()
        .map(file -> adapters.load(resourceSet, URI.createFileURI(file.toString())))
        .toList();
  }

  static List<Resource> loadRebased(Path folder, Path uriBase, AdapterRegistry adapters) {
    return loadRebased(ModelFiles.in(folder, adapters), folder, uriBase, adapters);
  }

  static List<Resource> loadRebased(
      List<Path> files, Path filesBase, Path uriBase, AdapterRegistry adapters) {
    ResourceSet resourceSet = adapters.newResourceSet();
    List<Resource> resources = new ArrayList<>();
    for (Path file : files) {
      resources.add(adapters.load(resourceSet, URI.createFileURI(file.toString())));
    }

    resources.forEach(EcoreUtil::resolveAll);
    Path base = filesBase.toAbsolutePath().normalize();
    for (Resource resource : List.copyOf(resourceSet.getResources())) {
      URI uri = resource.getURI();
      if (uri == null || !uri.isFile()) {
        continue;
      }

      Path file = Path.of(uri.toFileString()).toAbsolutePath().normalize();
      if (file.startsWith(base)) {
        resource.setURI(
            URI.createFileURI(uriBase.resolve(base.relativize(file).toString()).toString()));
      }
    }

    return resources;
  }

  static List<Resource> ownedBy(List<Resource> resources, Predicate<String> nsUri) {
    return resources.stream().filter(resource -> rootNsUris(resource).anyMatch(nsUri)).toList();
  }

  static List<Resource> notOwnedBy(List<Resource> resources, Predicate<String> nsUri) {
    return resources.stream().filter(resource -> rootNsUris(resource).noneMatch(nsUri)).toList();
  }

  static Predicate<String> ownedByAny(Collection<MetamodelNode> nodes) {
    return nsUri -> nodes.stream().anyMatch(node -> node.owns(nsUri));
  }

  static long changeCount(
      ModelStateDiff diff,
      AdapterRegistry adapters,
      List<Resource> current,
      List<Resource> proposed) {
    prepareForComparison(current, adapters);
    prepareForComparison(proposed, adapters);
    return diff.changeCount(current, proposed);
  }

  private static java.util.stream.Stream<String> rootNsUris(Resource resource) {
    return resource.getContents().stream().map(root -> root.eClass().getEPackage().getNsURI());
  }

  private static void prepareForComparison(List<Resource> resources, AdapterRegistry adapters) {
    resources.forEach(EcoreUtil::resolveAll);
    for (Resource resource : resources) {
      for (EObject root : resource.getContents()) {
        adapters.adapterFor(root).dropResolutionCaches(root);
      }
    }
  }
}
