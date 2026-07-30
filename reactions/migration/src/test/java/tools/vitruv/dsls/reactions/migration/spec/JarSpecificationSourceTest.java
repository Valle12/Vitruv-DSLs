package tools.vitruv.dsls.reactions.migration.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.dsls.reactions.migration.PropagationJars;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;

class JarSpecificationSourceTest {
  @SuppressWarnings("HttpUrlsUsage")
  private static final String UML_NS = "http://www.eclipse.org/uml2/5.0.0/UML";

  @SuppressWarnings("HttpUrlsUsage")
  private static final String JAVA_NS = "http://www.emftext.org/java";

  @TempDir Path tempDir;

  @Test
  @DisplayName("discovers UML-Java specifications from the jar by reflection")
  void test1() throws Exception {
    Path umljavaJar = PropagationJars.config1();

    try (JarSpecificationSource source = JarSpecificationSource.fromJar(umljavaJar)) {
      List<ChangePropagationSpecification> specs = source.createSpecifications();

      Set<String> simpleNames =
          specs.stream().map(spec -> spec.getClass().getSimpleName()).collect(Collectors.toSet());
      assertTrue(
          simpleNames.contains("UmlToJavaChangePropagationSpecification"),
          "UML->Java spec must be discovered, found: " + simpleNames);
      assertTrue(
          simpleNames.contains("JavaToUmlChangePropagationSpecification"),
          "Java->UML spec must be discovered, found: " + simpleNames);
      assertTrue(
          specs.stream().noneMatch(spec -> spec.getClass().getName().startsWith("mir.")),
          "generated mir.* superclasses must be filtered out: "
              + specs.stream().map(spec -> spec.getClass().getName()).toList());

      PropagationGraph graph = new PropagationGraph(specs);
      assertTrue(graph.nodeContaining(UML_NS).isPresent(), "graph must contain the UML metamodel");
      assertTrue(
          graph.nodeContaining(JAVA_NS).isPresent(), "graph must contain the Java metamodel");
    }
  }

  @Test
  @DisplayName("every call creates fresh specification instances")
  void test2() throws Exception {
    Path umljavaJar = PropagationJars.config1();

    try (JarSpecificationSource source = JarSpecificationSource.fromJar(umljavaJar)) {
      List<ChangePropagationSpecification> first = source.createSpecifications();
      List<ChangePropagationSpecification> second = source.createSpecifications();

      assertEquals(first.size(), second.size());
      for (int i = 0; i < first.size(); i++) {
        assertNotSame(first.get(i), second.get(i), "specs must not be shared between VSUMs");
      }
    }
  }

  @Test
  @DisplayName("missing jar fails")
  @SuppressWarnings("resource")
  void test3() {
    Path path = tempDir.resolve("missing.jar");
    assertThrows(IllegalArgumentException.class, () -> JarSpecificationSource.fromJar(path));
  }

  @Test
  @DisplayName("the rules come from the jar, not from the classpath")
  void test4() throws Exception {
    Set<String> config1 = PropagationJars.ruleIdsOf(PropagationJars.config1());
    Set<String> config7 = PropagationJars.ruleIdsOf(PropagationJars.config7());

    assertTrue(config1.contains("umlToJavaClassifier::UmlClassInserted"), config1.toString());
    assertTrue(config1.contains("umlToJavaClassifier::UmlDataTypeInserted"), config1.toString());
    assertFalse(config1.contains("umlToJavaClassifier::UmlClassInsertedAsInterface"));

    assertTrue(
        config7.contains("umlToJavaClassifier::UmlClassInsertedAsInterface"), config7.toString());
    assertTrue(
        config7.contains("umlToJavaClassifier::UmlDataTypeInsertedAsRecord"), config7.toString());
    assertTrue(
        config7.contains("javaToUmlClassifier::JavaClassInsertedAsInterface"), config7.toString());
    assertFalse(config7.contains("umlToJavaClassifier::UmlClassInserted"));
    assertFalse(config7.contains("javaToUmlClassifier::JavaClassInserted"));
  }
}
