package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import pcm_mockup.Component;
import pcm_mockup.PInterface;
import pcm_mockup.Pcm_mockupFactory;
import pcm_mockup.Repository;
import tools.vitruv.change.atomic.EChange;
import tools.vitruv.change.atomic.feature.FeatureEChange;
import tools.vitruv.change.atomic.feature.attribute.ReplaceSingleValuedEAttribute;
import tools.vitruv.change.atomic.feature.reference.ReplaceSingleValuedEReference;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import uml_mockup.UClass;
import uml_mockup.UPackage;
import uml_mockup.Uml_mockupFactory;

@ExtendWith(RegisterMetamodelsInStandalone.class)
class ChangeProbesTest {
  private static Set<String> changeKindsOf(List<EChange<EObject>> probes) {
    return probes.stream().map(probe -> probe.eClass().getName()).collect(Collectors.toSet());
  }

  private static Object valueOf(EChange<?> change) {
    EStructuralFeature newValue = change.eClass().getEStructuralFeature("newValue");
    return change.eGet(newValue);
  }

  @Test
  @DisplayName("every element gets a deletion probe, so rules that fire on deletion are selectable")
  void test5() {
    UClass uClass = Uml_mockupFactory.eINSTANCE.createUClass();

    assertTrue(
        changeKindsOf(ChangeProbes.forElement(uClass)).contains("DeleteEObject"),
        "repropagating an element really does delete it, so JavaClassifierDeleted-shaped rules"
            + " must be able to match it");
  }

  @Test
  @DisplayName("no probe claims a single feature value was removed, which never happens")
  void test6() {
    UPackage uPackage = Uml_mockupFactory.eINSTANCE.createUPackage();
    UClass uClass = Uml_mockupFactory.eINSTANCE.createUClass();
    uPackage.getClasses().add(uClass);

    Set<String> kinds = changeKindsOf(ChangeProbes.forElement(uPackage));
    assertFalse(
        kinds.contains("RemoveEReference") || kinds.contains("RemoveEAttributeValue"),
        "the tear-down removes whole elements, never one value of a feature: " + kinds);
  }

  @Test
  @DisplayName("a root element gets creation and root-insertion probes")
  void test1() {
    UPackage uPackage = Uml_mockupFactory.eINSTANCE.createUPackage();
    Resource resource = new ResourceImpl(URI.createURI("test:/model.uml_mockup"));
    resource.getContents().add(uPackage);

    assertTrue(
        changeKindsOf(ChangeProbes.forElement(uPackage))
            .containsAll(Set.of("CreateEObject", "InsertRootEObject")));
  }

  @Test
  @DisplayName("a contained element gets a containment-insertion probe from its own perspective")
  void test2() {
    UPackage uPackage = Uml_mockupFactory.eINSTANCE.createUPackage();
    UClass uClass = Uml_mockupFactory.eINSTANCE.createUClass();
    uPackage.getClasses().add(uClass);

    List<EChange<EObject>> probes = ChangeProbes.forElement(uClass);
    assertTrue(changeKindsOf(probes).containsAll(Set.of("CreateEObject", "InsertEReference")));
    assertTrue(
        probes.stream()
            .anyMatch(
                probe ->
                    probe instanceof FeatureEChange<?, ?> featureChange
                        && featureChange.getAffectedElement() == uPackage
                        && "classes".equals(featureChange.getAffectedFeature().getName())));
  }

  @Test
  @DisplayName("set attributes get replacement probes, unset attributes none")
  void test3() {
    UClass uClass = Uml_mockupFactory.eINSTANCE.createUClass();
    uClass.setName("Counter");

    List<EChange<EObject>> probes = ChangeProbes.forElement(uClass);
    List<String> probedAttributes =
        probes.stream()
            .filter(ReplaceSingleValuedEAttribute.class::isInstance)
            .map(probe -> ((FeatureEChange<?, ?>) probe).getAffectedFeature().getName())
            .toList();
    assertTrue(probedAttributes.contains("name"));
    assertFalse(probedAttributes.contains("classCount"), "unset attributes must not be probed");
  }

  @Test
  @DisplayName("set non-containment references get replacement probes with the referenced value")
  void test4() {
    Repository repository = Pcm_mockupFactory.eINSTANCE.createRepository();
    PInterface pInterface = Pcm_mockupFactory.eINSTANCE.createPInterface();
    Component component = Pcm_mockupFactory.eINSTANCE.createComponent();
    repository.getInterfaces().add(pInterface);
    repository.getComponents().add(component);
    component.setProvidedInterface(pInterface);

    assertTrue(
        ChangeProbes.forElement(component).stream()
            .anyMatch(
                probe ->
                    probe instanceof ReplaceSingleValuedEReference<?> replace
                        && "providedInterface".equals(replace.getAffectedFeature().getName())
                        && valueOf(replace) == pInterface));
  }
}
