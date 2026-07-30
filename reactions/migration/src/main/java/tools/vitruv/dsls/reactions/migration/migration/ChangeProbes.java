package tools.vitruv.dsls.reactions.migration.migration;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import tools.vitruv.change.atomic.EChange;
import tools.vitruv.change.atomic.TypeInferringAtomicEChangeFactory;

final class ChangeProbes {
  private static final TypeInferringAtomicEChangeFactory FACTORY =
      TypeInferringAtomicEChangeFactory.getInstance();

  private ChangeProbes() {}

  static List<EChange<EObject>> forElement(EObject element) {
    List<EChange<EObject>> probes = new ArrayList<>();
    probes.add(FACTORY.createCreateEObjectChange(element));
    probes.add(FACTORY.createDeleteEObjectChange(element));
    addContainmentInsertionProbe(probes, element);
    addAttributeProbes(probes, element);
    addCrossReferenceProbes(probes, element);
    return probes;
  }

  private static void addContainmentInsertionProbe(List<EChange<EObject>> probes, EObject element) {
    EObject container = element.eContainer();
    if (container == null) {
      if (element.eResource() != null) {
        probes.add(
            FACTORY.createInsertRootChange(
                element, element.eResource(), element.eResource().getContents().indexOf(element)));
      }

      return;
    }

    EReference containment = element.eContainmentFeature();
    if (containment.isMany()) {
      List<?> siblings = (List<?>) container.eGet(containment);
      probes.add(
          FACTORY.createInsertReferenceChange(
              container, containment, element, siblings.indexOf(element)));
    } else {
      probes.add(FACTORY.createReplaceSingleReferenceChange(container, containment, null, element));
    }
  }

  private static void addAttributeProbes(List<EChange<EObject>> probes, EObject element) {
    for (EAttribute attribute : element.eClass().getEAllAttributes()) {
      if (isProbeIrrelevant(attribute) || !element.eIsSet(attribute)) {
        continue;
      }

      if (attribute.isMany()) {
        List<?> values = (List<?>) element.eGet(attribute);
        for (int index = 0; index < values.size(); index++) {
          probes.add(
              FACTORY.createInsertAttributeChange(element, attribute, index, values.get(index)));
        }
      } else {
        probes.add(
            FACTORY.createReplaceSingleAttributeChange(
                element, attribute, attribute.getDefaultValue(), element.eGet(attribute)));
      }
    }
  }

  private static void addCrossReferenceProbes(List<EChange<EObject>> probes, EObject element) {
    for (EReference reference : element.eClass().getEAllReferences()) {
      if (reference.isContainment()
          || reference.isContainer()
          || isProbeIrrelevant(reference)
          || !element.eIsSet(reference)) {
        continue;
      }

      if (reference.isMany()) {
        List<?> values = (List<?>) element.eGet(reference);
        for (int index = 0; index < values.size(); index++) {
          probes.add(
              FACTORY.createInsertReferenceChange(
                  element, reference, (EObject) values.get(index), index));
        }
      } else {
        probes.add(
            FACTORY.createReplaceSingleReferenceChange(
                element, reference, null, (EObject) element.eGet(reference)));
      }
    }
  }

  private static boolean isProbeIrrelevant(EStructuralFeature feature) {
    return !feature.isChangeable() || feature.isDerived() || feature.isTransient();
  }
}
