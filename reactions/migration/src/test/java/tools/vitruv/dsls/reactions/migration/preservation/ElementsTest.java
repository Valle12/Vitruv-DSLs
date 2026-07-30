package tools.vitruv.dsls.reactions.migration.preservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.util.List;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ElementsTest {
  private final EClass node = nodeClass();
  @TempDir Path tempDir;

  private static EClass nodeClass() {
    EcoreFactory factory = EcoreFactory.eINSTANCE;
    EPackage metamodel = factory.createEPackage();
    metamodel.setName("chain");
    metamodel.setNsPrefix("chain");
    metamodel.setNsURI("http://vitruv.tools/test/chain");

    EClass node = factory.createEClass();
    node.setName("Node");
    EAttribute name = factory.createEAttribute();
    name.setName("name");
    name.setEType(EcorePackage.Literals.ESTRING);
    node.getEStructuralFeatures().add(name);
    EReference children = factory.createEReference();
    children.setName("children");
    children.setContainment(true);
    children.setUpperBound(-1);
    children.setEType(node);
    node.getEStructuralFeatures().add(children);

    metamodel.getEClassifiers().add(node);
    return node;
  }

  private EObject node(String name, EObject... children) {
    EObject element = EcoreUtil.create(node);
    if (name != null) {
      element.eSet(node.getEStructuralFeature("name"), name);
    }

    @SuppressWarnings("unchecked")
    List<EObject> contained = (List<EObject>) element.eGet(node.getEStructuralFeature("children"));
    contained.addAll(List.of(children));
    return element;
  }

  private void persistAt(EObject root, String file) {
    ResourceSetImpl resourceSet = new ResourceSetImpl();
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
    resourceSet
        .createResource(URI.createFileURI(tempDir.resolve(file).toString()))
        .getContents()
        .add(root);
  }

  @Test
  @DisplayName("an unnamed element carries the names of all its containers")
  void test1() {
    EObject leaf = node(null);
    persistAt(node("library", node("catalog", node("Member", leaf))), "model.xmi");

    assertEquals(
        "chain::Node in library.catalog.Member at"
            + " model.xmi#0/@children.0/@children.0/@children.0",
        Elements.describe(leaf, tempDir));
  }

  @Test
  @DisplayName("a named element keeps its own name and still names its containers")
  void test2() {
    EObject member = node("Member");
    persistAt(node("library", node("catalog", member)), "model.xmi");

    assertEquals(
        "chain::Node 'Member' in library.catalog at model.xmi#0/@children.0/@children.0",
        Elements.describe(member, tempDir));
  }

  @Test
  @DisplayName("a root whose name only restates the file is left out of the chain")
  void test3() {
    EObject statement = node(null);
    EObject compilationUnit = node("catalog.Member.java", node("Member", statement));
    persistAt(compilationUnit, "src/Member.java");

    assertEquals(
        "chain::Node in Member at src/Member.java#0/@children.0/@children.0",
        Elements.describe(statement, tempDir));
  }

  @Test
  @DisplayName("a chain without any named container renders as before")
  void test4() {
    EObject leaf = node(null);
    persistAt(node(null, leaf), "model.xmi");

    String description = Elements.describe(leaf, tempDir);

    assertEquals("chain::Node at model.xmi#0/@children.0", description);
    assertFalse(description.contains(" in "));
  }

  @Test
  @DisplayName("an element outside any resource still names its containers")
  void test5() {
    EObject leaf = node(null);
    node("detached", leaf);

    assertEquals("chain::Node in detached", Elements.describe(leaf, tempDir));
  }
}
