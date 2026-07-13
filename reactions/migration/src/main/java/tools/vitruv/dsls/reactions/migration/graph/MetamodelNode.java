package tools.vitruv.dsls.reactions.migration.graph;

import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;
import org.eclipse.emf.ecore.EPackage;
import tools.vitruv.change.composite.MetamodelDescriptor;

public record MetamodelNode(SortedSet<String> nsUris) {
  public MetamodelNode {
    nsUris = new TreeSet<>(nsUris);
  }

  public static MetamodelNode of(MetamodelDescriptor descriptor) {
    return new MetamodelNode(new TreeSet<>(descriptor.getNsUris()));
  }

  public static MetamodelNode of(Collection<String> nsUris) {
    return new MetamodelNode(new TreeSet<>(nsUris));
  }

  public boolean owns(String nsUri) {
    return declaresUriOrParentOf(nsUri) || declaresAncestorPackageOf(nsUri);
  }

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
