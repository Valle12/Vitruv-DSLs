package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.CLASS_INSERTED;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.CLASS_TRIGGER;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.INTERFACE_TRIGGER;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.METHOD_INSERTED;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.METHOD_TRIGGER;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.emf.ecore.EObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tools.vitruv.change.propagation.ConsistencyRuleId;
import tools.vitruv.change.propagation.ConsistencyRuleTrigger;
import tools.vitruv.change.propagation.ConsistencyRuleTriggerMatcher;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import uml_mockup.UClass;
import uml_mockup.UInterface;
import uml_mockup.UMethod;
import uml_mockup.UPackage;
import uml_mockup.Uml_mockupFactory;

@ExtendWith(RegisterMetamodelsInStandalone.class)
class AffectedElementsTest {
  private UPackage uPackage;
  private UClass uClass;
  private UInterface uInterface;
  private UMethod uMethod;

  private static ConsistencyRuleTriggerMatcher matcherFor(ConsistencyRuleTrigger trigger) {
    return ConsistencyRuleTriggerMatcher.of(trigger).orElseThrow();
  }

  @BeforeEach
  void createModel() {
    uPackage = Uml_mockupFactory.eINSTANCE.createUPackage();
    uClass = Uml_mockupFactory.eINSTANCE.createUClass();
    uInterface = Uml_mockupFactory.eINSTANCE.createUInterface();
    uMethod = Uml_mockupFactory.eINSTANCE.createUMethod();
    uPackage.getClasses().add(uClass);
    uPackage.getInterfaces().add(uInterface);
    uInterface.getMethods().add(uMethod);
  }

  @Test
  @DisplayName("finds exactly the elements whose reconstruction fires a dirty rule")
  void test1() {
    assertEquals(
        List.of(uClass),
        AffectedElements.find(List.of(uPackage), List.of(matcherFor(CLASS_TRIGGER))));
    assertEquals(
        List.of(uMethod),
        AffectedElements.find(List.of(uPackage), List.of(matcherFor(METHOD_TRIGGER))));
  }

  @Test
  @DisplayName("keeps only the outermost of nested matches")
  void test2() {
    assertEquals(
        List.of(uInterface),
        AffectedElements.find(
            List.of(uPackage), List.of(matcherFor(INTERFACE_TRIGGER), matcherFor(METHOD_TRIGGER))));
  }

  @Test
  @DisplayName("returns nothing when no rule matches")
  void test3() {
    assertEquals(
        List.of(), AffectedElements.find(List.of(uClass), List.of(matcherFor(METHOD_TRIGGER))));
  }

  @Test
  @DisplayName("attributes each selected element to the dirty rules whose triggers matched it")
  void test4() {
    AffectedElements.Selection selection =
        AffectedElements.select(
            List.of(uPackage),
            List.of(
                new DirtyMatcher(CLASS_INSERTED, matcherFor(CLASS_TRIGGER)),
                new DirtyMatcher(METHOD_INSERTED, matcherFor(METHOD_TRIGGER))),
            element -> false);

    Map<EObject, List<ConsistencyRuleId>> matchedBy =
        selection.elements().stream()
            .collect(
                Collectors.toMap(
                    AffectedElements.Selected::element, AffectedElements.Selected::matchedBy));
    assertEquals(
        Map.of(uClass, List.of(CLASS_INSERTED), uMethod, List.of(METHOD_INSERTED)), matchedBy);
    assertEquals(Map.of(CLASS_INSERTED, 1, METHOD_INSERTED, 1), selection.matchesPerRule());
    assertEquals(4, selection.probedElements(), "package, class, interface and method");
    assertTrue(selection.elements().stream().allMatch(each -> each.refersTo() == null));
  }
}
