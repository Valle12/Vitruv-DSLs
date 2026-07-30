package tools.vitruv.dsls.reactions.migration.testspecs;

import java.util.List;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import tools.vitruv.change.atomic.EChange;
import tools.vitruv.change.atomic.feature.FeatureEChange;
import tools.vitruv.change.atomic.feature.attribute.ReplaceSingleValuedEAttribute;
import tools.vitruv.change.atomic.feature.reference.InsertEReference;
import tools.vitruv.change.atomic.feature.reference.RemoveEReference;
import tools.vitruv.change.atomic.root.InsertRootEObject;
import tools.vitruv.change.composite.MetamodelDescriptor;
import tools.vitruv.change.correspondence.Correspondence;
import tools.vitruv.change.correspondence.view.EditableCorrespondenceModelView;
import tools.vitruv.change.propagation.impl.AbstractChangePropagationSpecification;
import tools.vitruv.change.utils.ResourceAccess;

public abstract class MirroringSpecification extends AbstractChangePropagationSpecification {
  private final String sourceNsUri;
  private final String targetNsUri;
  private final String targetFileExtension;
  private final String correspondenceTag;

  protected MirroringSpecification(
      EPackage sourcePackage, EPackage targetPackage, String targetFileExtension) {
    super(
        MetamodelDescriptor.with(sourcePackage.getNsURI()),
        MetamodelDescriptor.with(targetPackage.getNsURI()));
    this.sourceNsUri = sourcePackage.getNsURI();
    this.targetNsUri = targetPackage.getNsURI();
    this.targetFileExtension = targetFileExtension;
    this.correspondenceTag = sourcePackage.getName() + "-to-" + targetPackage.getName();
  }

  protected static void copyAttribute(
      EObject source, EObject mirror, EAttribute attribute, String targetFeatureName) {
    EStructuralFeature targetFeature = mirror.eClass().getEStructuralFeature(targetFeatureName);
    if (targetFeature != null) {
      mirror.eSet(targetFeature, source.eGet(attribute));
    }
  }

  protected abstract EObject mirrorRoot(EObject sourceRoot);

  protected abstract EObject mirrorChild(EObject mirrorParent, EObject sourceChild);

  protected abstract void syncAttribute(EObject source, EObject mirror, EAttribute attribute);

  @Override
  public boolean doesHandleChange(
      EChange<EObject> change, EditableCorrespondenceModelView<Correspondence> correspondences) {
    EObject changed = changedElement(change);
    return changed != null && belongsToSourceMetamodel(changed);
  }

  @Override
  @SuppressWarnings("unchecked")
  public void propagateChange(
      EChange<EObject> change,
      EditableCorrespondenceModelView<Correspondence> correspondences,
      ResourceAccess resourceAccess) {
    if (!doesHandleChange(change, correspondences)) {
      return;
    }

    if (change instanceof InsertRootEObject<?> insert) {
      mirrorRootInsertion((InsertRootEObject<EObject>) insert, correspondences, resourceAccess);
    } else if (change instanceof InsertEReference<?> insert
        && insert.getAffectedFeature().isContainment()) {
      mirrorChildInsertion((InsertEReference<EObject>) insert, correspondences);
    } else if (change instanceof ReplaceSingleValuedEAttribute<?, ?> replace) {
      mirrorAttributeReplacement(
          (ReplaceSingleValuedEAttribute<EObject, ?>) replace, correspondences);
    } else if (change instanceof RemoveEReference<?> remove
        && remove.getAffectedFeature().isContainment()) {
      mirrorChildRemoval((RemoveEReference<EObject>) remove, correspondences);
    }
  }

  private void mirrorRootInsertion(
      InsertRootEObject<EObject> insert,
      EditableCorrespondenceModelView<Correspondence> correspondences,
      ResourceAccess resourceAccess) {
    EObject sourceRoot = insert.getNewValue();
    if (!mirrorsOf(sourceRoot, correspondences).isEmpty()) {
      return;
    }

    EObject mirror = mirrorRoot(sourceRoot);
    if (mirror == null) {
      return;
    }

    resourceAccess.persistAsRoot(mirror, mirrorUriFor(insert.getUri()));
    correspondences.addCorrespondenceBetween(sourceRoot, mirror, correspondenceTag);
    syncAllAttributes(sourceRoot, mirror);
  }

  private void mirrorChildInsertion(
      InsertEReference<EObject> insert,
      EditableCorrespondenceModelView<Correspondence> correspondences) {
    EObject parent = insert.getAffectedElement();
    EObject child = insert.getNewValue();
    if (!mirrorsOf(child, correspondences).isEmpty()) {
      return;
    }

    for (EObject mirrorParent : mirrorsOf(parent, correspondences)) {
      EObject mirrorChild = mirrorChild(mirrorParent, child);
      if (mirrorChild != null) {
        correspondences.addCorrespondenceBetween(child, mirrorChild, correspondenceTag);
        syncAllAttributes(child, mirrorChild);
      }
    }
  }

  private void mirrorAttributeReplacement(
      ReplaceSingleValuedEAttribute<EObject, ?> replace,
      EditableCorrespondenceModelView<Correspondence> correspondences) {
    EObject element = replace.getAffectedElement();
    for (EObject mirror : mirrorsOf(element, correspondences)) {
      syncAttribute(element, mirror, replace.getAffectedFeature());
    }
  }

  private void mirrorChildRemoval(
      RemoveEReference<EObject> remove,
      EditableCorrespondenceModelView<Correspondence> correspondences) {
    EObject removed = remove.getOldValue();
    for (EObject mirror : mirrorsOf(removed, correspondences)) {
      correspondences.removeCorrespondencesBetween(removed, mirror, correspondenceTag);
      EcoreUtil.delete(mirror);
    }
  }

  private List<EObject> mirrorsOf(
      EObject element, EditableCorrespondenceModelView<Correspondence> correspondences) {
    return correspondences.getCorrespondingEObjects(element).stream()
        .filter(
            corresponding -> targetNsUri.equals(corresponding.eClass().getEPackage().getNsURI()))
        .toList();
  }

  private void syncAllAttributes(EObject source, EObject mirror) {
    for (EAttribute attribute : source.eClass().getEAllAttributes()) {
      if (source.eIsSet(attribute)) {
        syncAttribute(source, mirror, attribute);
      }
    }
  }

  private boolean belongsToSourceMetamodel(EObject element) {
    return sourceNsUri.equals(element.eClass().getEPackage().getNsURI());
  }

  private EObject changedElement(EChange<EObject> change) {
    if (change instanceof InsertRootEObject<?> insert) {
      return (EObject) insert.getNewValue();
    }

    if (change instanceof FeatureEChange<?, ?> feature) {
      return (EObject) feature.getAffectedElement();
    }

    return null;
  }

  protected URI mirrorUriFor(String sourceUri) {
    return URI.createURI(sourceUri).trimFileExtension().appendFileExtension(targetFileExtension);
  }
}
