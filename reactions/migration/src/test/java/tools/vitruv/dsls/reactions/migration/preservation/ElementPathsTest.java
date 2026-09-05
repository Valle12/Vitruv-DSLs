package tools.vitruv.dsls.reactions.migration.preservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import allElementTypes.AllElementTypesFactory;
import allElementTypes.NonRoot;
import allElementTypes.Root;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;

@ExtendWith(RegisterMetamodelsInStandalone.class)
class ElementPathsTest {
  @TempDir Path tempDir;

  private static Root rootWithChildren(String... childValues) {
    Root root = AllElementTypesFactory.eINSTANCE.createRoot();
    root.setId("root");
    for (String value : childValues) {
      NonRoot child = AllElementTypesFactory.eINSTANCE.createNonRoot();
      child.setId(value);
      child.setValue(value);
      root.getMultiValuedContainmentEReference().add(child);
    }

    return root;
  }

  private Resource resourceAt(EObject... roots) {
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
    Resource resource =
        resourceSet.createResource(
            URI.createFileURI(tempDir.resolve("models/model.allelementtypes").toString()));
    resource.getContents().addAll(List.of(roots));
    return resource;
  }

  @Test
  @DisplayName("a path locates the same element in a structurally identical model")
  void test1() {
    Root original = rootWithChildren("first", "second");
    resourceAt(original);
    NonRoot second = original.getMultiValuedContainmentEReference().get(1);

    String path = ElementPaths.pathOf(second);
    Root copy = EcoreUtil.copy(original);

    assertEquals("0/@multiValuedContainmentEReference.1", path);
    assertSame(
        copy.getMultiValuedContainmentEReference().get(1),
        ElementPaths.locate(List.of(copy), path));
  }

  @Test
  @DisplayName("the path of a root is the root itself")
  void test2() {
    Root root = rootWithChildren();
    resourceAt(root);

    assertSame(root, ElementPaths.locate(List.of(root), ElementPaths.pathOf(root)));
  }

  @Test
  @DisplayName("the second root of a resource keeps its index")
  void test3() {
    Root first = rootWithChildren("a");
    Root second = rootWithChildren("b");
    resourceAt(first, second);

    assertEquals("1/", ElementPaths.pathOf(second));
    assertSame(second, ElementPaths.locate(List.of(first, second), ElementPaths.pathOf(second)));
  }

  @Test
  @DisplayName("a path that does not exist in the other model resolves to nothing")
  void test4() {
    Root original = rootWithChildren("first", "second");
    resourceAt(original);
    String path = ElementPaths.pathOf(original.getMultiValuedContainmentEReference().get(1));

    Root fewerChildren = rootWithChildren("first");

    assertNull(ElementPaths.locate(List.of(fewerChildren), path));
    assertNull(ElementPaths.locate(List.of(), path));
  }

  @Test
  @DisplayName("resource keys are relative to the vsum folder")
  void test5() {
    Root root = rootWithChildren();
    Resource resource = resourceAt(root);

    assertEquals("models/model.allelementtypes", ElementPaths.resourceKey(resource, tempDir));
    assertNull(
        ElementPaths.resourceKey(resource, tempDir.resolve("elsewhere")),
        "a model outside the folder has no key there");
    assertNotNull(ElementPaths.pathOf(root));
  }
}
