package tools.vitruv.dsls.reactions.migration.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tools.vitruv.dsls.reactions.migration.TestSpecifications.JAVA;
import static tools.vitruv.dsls.reactions.migration.TestSpecifications.PCM;
import static tools.vitruv.dsls.reactions.migration.TestSpecifications.UML;
import static tools.vitruv.dsls.reactions.migration.TestSpecifications.spec;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PropagationGraphTest {
  @Test
  @DisplayName("from specifications registers both endpoints")
  void test1() {
    PropagationGraph graph = new PropagationGraph(List.of(spec(UML, JAVA)));

    assertTrue(graph.nodeContaining(UML).isPresent());
    assertTrue(graph.nodeContaining(JAVA).isPresent());
  }

  @Test
  @DisplayName("out degree counts direct neighbors")
  void test2() {
    PropagationGraph graph =
        new PropagationGraph(List.of(spec(UML, JAVA), spec(UML, PCM), spec(JAVA, UML)));

    MetamodelNode umlNode = graph.nodeContaining(UML).orElseThrow();
    MetamodelNode javaNode = graph.nodeContaining(JAVA).orElseThrow();
    MetamodelNode pcmNode = graph.nodeContaining(PCM).orElseThrow();

    assertEquals(2, graph.outDegree(umlNode));
    assertEquals(1, graph.outDegree(javaNode));
    assertEquals(0, graph.outDegree(pcmNode));
  }

  @Test
  @DisplayName("reachable from computes the transitive closure")
  void test3() {
    PropagationGraph graph = new PropagationGraph(List.of(spec(UML, JAVA), spec(JAVA, PCM)));

    MetamodelNode umlNode = graph.nodeContaining(UML).orElseThrow();
    MetamodelNode javaNode = graph.nodeContaining(JAVA).orElseThrow();
    MetamodelNode pcmNode = graph.nodeContaining(PCM).orElseThrow();

    Set<MetamodelNode> fromUml = graph.reachableFrom(umlNode);
    assertTrue(fromUml.contains(javaNode));
    assertTrue(fromUml.contains(pcmNode));
    assertEquals(2, fromUml.size());
    assertTrue(graph.reachableFrom(pcmNode).isEmpty());
  }

  @Test
  @DisplayName("reachable from handles cycles without infinite loop")
  void test4() {
    PropagationGraph graph = new PropagationGraph(List.of(spec(UML, JAVA), spec(JAVA, UML)));

    MetamodelNode umlNode = graph.nodeContaining(UML).orElseThrow();
    MetamodelNode javaNode = graph.nodeContaining(JAVA).orElseThrow();

    Set<MetamodelNode> fromUml = graph.reachableFrom(umlNode);
    assertTrue(fromUml.contains(javaNode));
    assertTrue(fromUml.contains(umlNode));
  }

  @Test
  @DisplayName("reaches all is true when a single source dominates")
  void test5() {
    PropagationGraph graph = new PropagationGraph(List.of(spec(UML, JAVA), spec(UML, PCM)));

    MetamodelNode umlNode = graph.nodeContaining(UML).orElseThrow();
    MetamodelNode javaNode = graph.nodeContaining(JAVA).orElseThrow();
    MetamodelNode pcmNode = graph.nodeContaining(PCM).orElseThrow();

    assertTrue(graph.reachesAll(umlNode, Set.of(umlNode, javaNode, pcmNode)));
    assertFalse(graph.reachesAll(javaNode, Set.of(umlNode, javaNode, pcmNode)));
  }

  @Test
  @DisplayName("node containing returns an empty optional for an unknown URI")
  void test6() {
    PropagationGraph graph = new PropagationGraph(List.of(spec(UML, JAVA)));

    assertTrue(graph.nodeContaining("http://does/not/exist").isEmpty());
  }

  @Test
  @DisplayName("empty specifications produce an empty graph")
  void test7() {
    PropagationGraph graph = new PropagationGraph(List.of());

    assertTrue(graph.nodeContaining(UML).isEmpty());
    assertTrue(graph.nodes().isEmpty());
    assertTrue(graph.toString().startsWith("propagation graph:"));
  }
}
