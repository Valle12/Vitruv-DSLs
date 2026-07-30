package tools.vitruv.dsls.reactions.migration.preservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import allElementTypes.AllElementTypesFactory;
import allElementTypes.AllElementTypesPackage;
import allElementTypes.NonRoot;
import allElementTypes.NonRootObjectContainerHelper;
import allElementTypes.Root;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SlotResolverTest {
  private static final AllElementTypesPackage TYPES = AllElementTypesPackage.eINSTANCE;

  private static Root root() {
    Root root = AllElementTypesFactory.eINSTANCE.createRoot();
    root.setId("root");
    return root;
  }

  private static NonRoot nonRoot() {
    NonRoot nonRoot = AllElementTypesFactory.eINSTANCE.createNonRoot();
    nonRoot.setId("child");
    return nonRoot;
  }

  private static NonRootObjectContainerHelper helper() {
    NonRootObjectContainerHelper helper =
        AllElementTypesFactory.eINSTANCE.createNonRootObjectContainerHelper();
    helper.setId("helper");
    return helper;
  }

  @Test
  @DisplayName("the identically named feature wins")
  void test1() {
    EStructuralFeature multiValued = TYPES.getRoot_MultiValuedContainmentEReference();

    SlotResolution resolution = SlotResolver.findSlot(root(), multiValued, nonRoot());

    assertTrue(resolution.isResolved());
    assertEquals(multiValued, resolution.slot().feature());
  }

  @Test
  @DisplayName("without that name the only feature accepting the value is used")
  void test2() {
    SlotResolution resolution =
        SlotResolver.findSlot(
            helper(), TYPES.getRoot_MultiValuedContainmentEReference(), nonRoot());

    assertTrue(resolution.isResolved());
    assertEquals(
        TYPES.getNonRootObjectContainerHelper_NonRootObjectsContainment(),
        resolution.slot().feature());
  }

  @Test
  @DisplayName("several fitting features are offered instead of guessed")
  void test3() {
    SlotResolution resolution =
        SlotResolver.findSlot(
            root(), TYPES.getNonRootObjectContainerHelper_NonRootObjectsContainment(), nonRoot());

    assertFalse(resolution.isResolved());
    assertEquals(LossReason.AMBIGUOUS_FEATURE, resolution.reason());
    assertTrue(
        resolution.alternatives().size() > 1,
        "the features somebody has to choose between: " + resolution.alternatives());
    assertTrue(
        resolution.alternatives().contains(TYPES.getRoot_MultiValuedContainmentEReference()),
        "every feature that could hold it is offered: " + resolution.alternatives());
  }

  @Test
  @DisplayName("a metaclass that cannot hold the value at all is a loss")
  void test4() {
    SlotResolution resolution =
        SlotResolver.findSlot(
            nonRoot(), TYPES.getRoot_MultiValuedContainmentEReference(), nonRoot());

    assertFalse(resolution.isResolved());
    assertEquals(LossReason.NO_COMPATIBLE_FEATURE, resolution.reason());
  }

  @Test
  @DisplayName("a single-valued feature the new rules already filled is a loss")
  void test5() {
    Root root = root();
    root.setSingleValuedContainmentEReference(nonRoot());

    SlotResolution located =
        SlotResolver.findSlot(root, TYPES.getRoot_SingleValuedContainmentEReference(), nonRoot());
    SlotResolution room = SlotResolver.checkCapacity(located.slot());

    assertTrue(located.isResolved(), "the feature is the right one, it simply has no room left");
    assertFalse(room.isResolved());
    assertEquals(LossReason.SLOT_OCCUPIED, room.reason());
  }

  @Test
  @DisplayName("attributes of a primitive type accept their boxed value")
  void test6() {
    SlotResolution resolution =
        SlotResolver.findSlot(root(), TYPES.getRoot_SingleValuedPrimitiveTypeEAttribute(), 42);

    assertTrue(resolution.isResolved());
    assertEquals(TYPES.getRoot_SingleValuedPrimitiveTypeEAttribute(), resolution.slot().feature());
  }

  @Test
  @DisplayName("a containment child never lands in a plain reference")
  void test7() {
    Root root = root();
    NonRoot child = nonRoot();
    root.getMultiValuedContainmentEReference().add(child);

    SlotResolution resolution =
        SlotResolver.findSlot(root, TYPES.getRoot_MultiValuedNonContainmentEReference(), child);

    assertTrue(resolution.isResolved());
    assertEquals(TYPES.getRoot_MultiValuedNonContainmentEReference(), resolution.slot().feature());
    assertFalse(
        SlotResolver.holds(resolution.slot(), child),
        "the value is contained, but the reference does not hold it yet");
  }

  @Test
  @DisplayName("a value the migrated model already holds is not re-attached")
  void test8() {
    Root root = root();
    NonRoot child = nonRoot();
    root.getMultiValuedNonContainmentEReference().add(child);

    SlotResolution resolution =
        SlotResolver.findSlot(root, TYPES.getRoot_MultiValuedNonContainmentEReference(), child);

    assertTrue(SlotResolver.holds(resolution.slot(), child));
  }
}
