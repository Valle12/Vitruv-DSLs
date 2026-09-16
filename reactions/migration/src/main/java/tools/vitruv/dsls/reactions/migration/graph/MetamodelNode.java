package tools.vitruv.dsls.reactions.migration.graph;

import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;
import org.eclipse.emf.ecore.EPackage;
import tools.vitruv.change.composite.MetamodelDescriptor;

/**
 * One metamodel of the propagation graph, identified by the namespace URIs it declares.
 *
 * @param nsUris the namespace URIs of the metamodel, held sorted so that two nodes declaring
 *     the same URIs compare as equal
 */
public record MetamodelNode(SortedSet<String> nsUris) {
  /** Copies the given URIs into a sorted set, so that the node compares by its content. */
  public MetamodelNode {
    nsUris = new TreeSet<>(nsUris);
  }

  /**
   * Creates the node of the metamodel the given descriptor names.
   *
   * @param descriptor the descriptor a change propagation specification carries
   * @return the node of that metamodel
   */
  public static MetamodelNode of(MetamodelDescriptor descriptor) {
    return new MetamodelNode(new TreeSet<>(descriptor.getNsUris()));
  }

  /**
   * Creates the node of the metamodel with the given namespace URIs.
   *
   * @param nsUris the namespace URIs of the metamodel
   * @return the node of that metamodel
   */
  public static MetamodelNode of(Collection<String> nsUris) {
    return new MetamodelNode(new TreeSet<>(nsUris));
  }

  /**
   * Returns whether the given namespace URI belongs to this metamodel. It does when the node
   * declares the URI or a URI it extends, and also when the package behind it is nested in a
   * package the node declares.
   *
   * @param nsUri the namespace URI to test
   * @return whether the URI belongs to this metamodel
   */
  public boolean owns(String nsUri) {
    return declaresUriOrParentOf(nsUri) || declaresAncestorPackageOf(nsUri);
  }

  /**
   * Returns the name this metamodel is reported under.
   *
   * @return the first of its namespace URIs, or {@code <none>} when it declares none
   */
  public String shortName() {
    return nsUris.isEmpty() ? "<none>" : nsUris.first();
  }

  private boolean declaresUriOrParentOf(String nsUri) {
    return nsUris.stream()
        .anyMatch(declaredUri -> nsUri.equals(declaredUri) || nsUri.startsWith(declaredUri + "/"));
  }

  private boolean declaresAncestorPackageOf(String nsUri) {
    EPackage owned = EPackage.Registry.INSTANCE.getEPackage(nsUri);
    if (owned == null) {
      return false;
    }

    EPackage ancestor = owned.getESuperPackage();
    while (ancestor != null) {
      if (declaresUriOrParentOf(ancestor.getNsURI())) {
        return true;
      }

      ancestor = ancestor.getESuperPackage();
    }

    return false;
  }
}
