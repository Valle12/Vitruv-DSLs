package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import allElementTypes.AllElementTypesFactory;
import allElementTypes.NonRoot;
import allElementTypes.Root;
import java.util.List;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;

@ExtendWith(RegisterMetamodelsInStandalone.class)
class SourceStateMergeTest {
  private static Resource resourceWith(Root root) {
    Resource resource = new ResourceImpl(URI.createURI("test:/model.allElementTypes"));
    resource.getContents().add(root);
    return resource;
  }

  private static Root root(List<Integer> attributeValues, String... childNames) {
    Root root = AllElementTypesFactory.eINSTANCE.createRoot();
    root.setId("theRoot");
    root.getMultiValuedEAttribute().addAll(attributeValues);
    for (String childName : childNames) {
      NonRoot child = AllElementTypesFactory.eINSTANCE.createNonRoot();
      child.setId(childName);
      root.getMultiValuedContainmentEReference().add(child);
    }

    return root;
  }

  @Test
  @DisplayName("content the counter propagation no longer produces is a retraction, not an ignore")
  void test1() {
    Resource live = resourceWith(root(List.of(1, 2), "kept", "dropped"));
    Resource desired = resourceWith(root(List.of(1), "kept"));

    SourceStateMerge.Delta delta = SourceStateMerge.between(live, desired);

    assertFalse(delta.retractions().isEmpty(), "the live model holds more than the rules produce");
    assertTrue(
        delta.describeRetractions().stream().anyMatch(item -> item.contains("NonRoot")),
        "and the report names what goes: " + delta.describeRetractions());
  }

  @Test
  @DisplayName("applying the delta actually removes it from the live model")
  void test2() {
    Resource live = resourceWith(root(List.of(1, 2), "kept", "dropped"));
    Resource desired = resourceWith(root(List.of(1), "kept"));

    SourceStateMerge.between(live, desired).apply();

    Root merged = (Root) live.getContents().getFirst();
    assertEquals(
        List.of("kept"),
        merged.getMultiValuedContainmentEReference().stream().map(NonRoot::getId).toList(),
        "the element the rules no longer derive is gone");
    assertEquals(List.of(1), merged.getMultiValuedEAttribute(), "and so is the attribute value");
  }

  @Test
  @DisplayName("the round delta counts retractions, so a retract-only round is not 'converged'")
  void test3() {
    Resource live = resourceWith(root(List.of(1, 2), "kept", "dropped"));
    Resource desired = resourceWith(root(List.of(1), "kept"));

    SourceStateMerge.Summary summary = SourceStateMerge.count(List.of(live), List.of(desired));

    assertEquals(0, summary.additions(), "this state proposes nothing new");
    assertTrue(summary.retractions() > 0, "but it does propose removals");
    assertTrue(
        summary.total() > 0,
        "counting only additions would report delta 0 and call the round converged");
  }

  @Test
  @DisplayName("an agreeing state proposes nothing in either direction")
  void test4() {
    Resource live = resourceWith(root(List.of(1), "kept"));
    Resource desired = resourceWith(root(List.of(1), "kept"));

    SourceStateMerge.Delta delta = SourceStateMerge.between(live, desired);

    assertTrue(delta.isEmpty(), "nothing to do: " + delta);
    assertEquals(0, SourceStateMerge.count(List.of(live), List.of(desired)).total());
  }
}
