package tools.vitruv.dsls.reactions.migration.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tools.vitruv.dsls.reactions.migration.TestSpecifications.JAVA;
import static tools.vitruv.dsls.reactions.migration.TestSpecifications.UML;
import static tools.vitruv.dsls.reactions.migration.TestSpecifications.spec;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.vitruv.dsls.reactions.migration.TestDominanceContexts;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;

class ExplicitDominanceTest {
  @Test
  @DisplayName("selects the node whose URI contains the token case-insensitively")
  void test1() {
    PropagationGraph graph = new PropagationGraph(List.of(spec(UML, JAVA)));
    MetamodelNode umlNode = graph.nodeContaining(UML).orElseThrow();
    MetamodelNode javaNode = graph.nodeContaining(JAVA).orElseThrow();
    Set<MetamodelNode> present = Set.of(umlNode, javaNode);

    assertEquals(
        Optional.of(umlNode),
        new ExplicitDominance("UML")
            .selectDominant(TestDominanceContexts.structuralOnly(graph, present)));
    assertEquals(
        Optional.of(javaNode),
        new ExplicitDominance("emftext")
            .selectDominant(TestDominanceContexts.structuralOnly(graph, present)));
  }

  @Test
  @DisplayName("unknown token selects nothing")
  void test2() {
    PropagationGraph graph = new PropagationGraph(List.of(spec(UML, JAVA)));
    MetamodelNode umlNode = graph.nodeContaining(UML).orElseThrow();

    assertTrue(
        new ExplicitDominance("pcm")
            .selectDominant(TestDominanceContexts.structuralOnly(graph, Set.of(umlNode)))
            .isEmpty());
  }
}
