package tools.vitruv.dsls.reactions.migration.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.applications.umljava.UmlToJavaChangePropagationSpecification;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;

class JarSpecificationSourceTest {
  private static final String UML_NS = "http://www.eclipse.org/uml2/5.0.0/UML";
  private static final String JAVA_NS = "http://www.emftext.org/java";

  @TempDir Path tempDir;

  private static Path jarContaining() throws Exception {
    return Path.of(
        UmlToJavaChangePropagationSpecification.class
            .getProtectionDomain()
            .getCodeSource()
            .getLocation()
            .toURI());
  }

  @Test
  @DisplayName("discovers UML-Java specifications from the jar by reflection")
  void test1() throws Exception {
    Path umljavaJar = jarContaining();
    Assumptions.assumeTrue(
        umljavaJar.toString().endsWith(".jar"), "umljava must be packaged as a jar to scan");

    List<ChangePropagationSpecification> specs =
        JarSpecificationSource.fromJar(umljavaJar).createSpecifications();

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
    assertTrue(graph.nodeContaining(JAVA_NS).isPresent(), "graph must contain the Java metamodel");
  }

  @Test
  @DisplayName("every call creates fresh specification instances")
  void test2() throws Exception {
    Path umljavaJar = jarContaining();
    Assumptions.assumeTrue(umljavaJar.toString().endsWith(".jar"));

    JarSpecificationSource source = JarSpecificationSource.fromJar(umljavaJar);
    List<ChangePropagationSpecification> first = source.createSpecifications();
    List<ChangePropagationSpecification> second = source.createSpecifications();

    assertEquals(first.size(), second.size());
    for (int i = 0; i < first.size(); i++) {
      assertNotSame(first.get(i), second.get(i), "specs must not be shared between VSUMs");
    }
  }

  @Test
  @DisplayName("missing jar fails")
  void test3() {
    Path path = tempDir.resolve("missing.jar");
    assertThrows(IllegalArgumentException.class, () -> JarSpecificationSource.fromJar(path));
  }
}
