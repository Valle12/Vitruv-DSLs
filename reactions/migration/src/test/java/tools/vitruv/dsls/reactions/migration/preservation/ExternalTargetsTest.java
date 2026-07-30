package tools.vitruv.dsls.reactions.migration.preservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import allElementTypes.AllElementTypesFactory;
import allElementTypes.Root;
import java.nio.file.Path;
import java.util.Objects;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.emftext.language.java.classifiers.ClassifiersFactory;
import org.emftext.language.java.classifiers.ConcreteClassifier;
import org.emftext.language.java.containers.CompilationUnit;
import org.emftext.language.java.containers.ContainersFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;

class ExternalTargetsTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();

  @TempDir Path tempDir;

  private static Root rootNamed(String id) {
    Root root = AllElementTypesFactory.eINSTANCE.createRoot();
    root.setId(id);
    return root;
  }

  private ExternalTargets targetsOf(Path oldFolder, Path newFolder) {
    return new ExternalTargets(ADAPTERS, oldFolder, newFolder);
  }

  private void resourceAt(URI uri, EObject root) {
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
    Resource resource = resourceSet.createResource(uri);
    resource.getContents().add(root);
  }

  @Test
  @DisplayName("an element inside a compared folder is not external")
  void test1() {
    Path oldFolder = tempDir.resolve("old");
    Path newFolder = tempDir.resolve("new");
    Root root = rootNamed("inside");
    resourceAt(URI.createFileURI(oldFolder.resolve("model.xmi").toString()), root);

    assertFalse(targetsOf(oldFolder, newFolder).isExternal(root));
  }

  @Test
  @DisplayName("an element in neither folder is external, and a non-element never is")
  void test2() {
    Path oldFolder = tempDir.resolve("old");
    Path newFolder = tempDir.resolve("new");
    Root elsewhere = rootNamed("elsewhere");
    resourceAt(URI.createFileURI(tempDir.resolve("outside.xmi").toString()), elsewhere);

    ExternalTargets targets = targetsOf(oldFolder, newFolder);
    assertTrue(targets.isExternal(elsewhere));
    assertFalse(targets.isExternal("a string"));
    assertFalse(targets.isExternal(null));
  }

  private ConcreteClassifier classpathClassifier(String jdkLocation) {
    var classifier = ClassifiersFactory.eINSTANCE.createClass();
    classifier.setName("String");
    CompilationUnit unit = ContainersFactory.eINSTANCE.createCompilationUnit();
    unit.setName("String.java");
    unit.getNamespaces().addAll(java.util.List.of("java", "lang"));
    unit.getClassifiers().add(classifier);
    resourceAt(URI.createURI(jdkLocation), unit);
    return classifier;
  }

  @Test
  @DisplayName("a classifier is named by its qualified name, not by where it was resolved from")
  void test3() {
    ConcreteClassifier fromOneJdk =
        classpathClassifier("archive:file:/one/jdk/java.base.jmod!/classes/java/lang/String.class");
    ConcreteClassifier fromAnotherJdk =
        classpathClassifier(
            "archive:file:/another/jdk/java.base.jmod!/classes/java/lang/String.class");

    ExternalTargets targets = targetsOf(tempDir.resolve("old"), tempDir.resolve("new"));
    assertEquals(
        "java.lang.String",
        targets.identityOf(fromOneJdk),
        "a classpath classifier is named by its qualified name");
    assertEquals(
        targets.identityOf(fromOneJdk),
        targets.identityOf(fromAnotherJdk),
        "the same classifier read from two JDKs must compare equal, or every run reports a change");
  }

  @Test
  @DisplayName(
      "without a metamodel identity the uri is used, relative to whichever folder holds it")
  void test4() {
    Path oldFolder = tempDir.resolve("old");
    Path newFolder = tempDir.resolve("new");
    Root inOld = rootNamed("library");
    Root inNew = rootNamed("library");
    resourceAt(URI.createFileURI(oldFolder.resolve("lib/types.xmi").toString()), inOld);
    resourceAt(URI.createFileURI(newFolder.resolve("lib/types.xmi").toString()), inNew);

    ExternalTargets targets = targetsOf(oldFolder, newFolder);
    assertEquals(
        targets.identityOf(inOld),
        targets.identityOf(inNew),
        "the same library file under each folder must compare equal");
    assertTrue(Objects.requireNonNull(targets.identityOf(inOld)).startsWith("lib/types.xmi"));
  }

  @Test
  @DisplayName("a different target yields a different identity")
  void test5() {
    Root one = rootNamed("one");
    Root other = rootNamed("other");
    resourceAt(URI.createURI("pathmap://LIBS/one.xmi"), one);
    resourceAt(URI.createURI("pathmap://LIBS/other.xmi"), other);

    ExternalTargets targets = targetsOf(tempDir.resolve("old"), tempDir.resolve("new"));
    org.junit.jupiter.api.Assertions.assertNotEquals(
        targets.identityOf(one), targets.identityOf(other));
  }

  @Test
  @DisplayName("an element nothing can name stays unnamed, so the report stays quiet about it")
  void test6() {
    Root detached = rootNamed("detached");
    ((InternalEObject) detached).eSetProxyURI(null);

    ExternalTargets targets = targetsOf(tempDir.resolve("old"), tempDir.resolve("new"));
    assertNull(targets.identityOf(detached), "no resource and no proxy uri means no identity");
  }
}
