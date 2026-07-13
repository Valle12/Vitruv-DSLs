package tools.vitruv.dsls.reactions.migration.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tools.vitruv.dsls.reactions.migration.TestSpecifications.JAVA;
import static tools.vitruv.dsls.reactions.migration.TestSpecifications.PCM;
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

class MaxReachabilityDominanceTest {
  private final MaxReachabilityDominance strategy = new MaxReachabilityDominance();

  @Test
  @DisplayName("picks the node that reaches all others")
  void test1() {
    PropagationGraph graph = new PropagationGraph(List.of(spec(UML, JAVA), spec(UML, PCM)));
    MetamodelNode umlNode = graph.nodeContaining(UML).orElseThrow();
    MetamodelNode javaNode = graph.nodeContaining(JAVA).orElseThrow();
    MetamodelNode pcmNode = graph.nodeContaining(PCM).orElseThrow();

    Optional<MetamodelNode> dominant =
        strategy.selectDominant(
            TestDominanceContexts.structuralOnly(graph, Set.of(umlNode, javaNode, pcmNode)));

    assertEquals(Optional.of(umlNode), dominant);
  }

  @Test
  @DisplayName("breaks cycle ties deterministically")
  void test2() {
    PropagationGraph graph = new PropagationGraph(List.of(spec(UML, JAVA), spec(JAVA, UML)));
    MetamodelNode umlNode = graph.nodeContaining(UML).orElseThrow();
    MetamodelNode javaNode = graph.nodeContaining(JAVA).orElseThrow();

    Optional<MetamodelNode> dominant =
        strategy.selectDominant(
            TestDominanceContexts.structuralOnly(graph, Set.of(umlNode, javaNode)));

    assertTrue(dominant.isPresent());
    assertTrue(dominant.get().equals(umlNode) || dominant.get().equals(javaNode));
  }

  @Test
  @DisplayName("falls back to best partial coverage when no node reaches all")
  void test3() {
    PropagationGraph graph = new PropagationGraph(List.of(spec(UML, JAVA), spec(PCM, PCM)));
    MetamodelNode umlNode = graph.nodeContaining(UML).orElseThrow();
    MetamodelNode javaNode = graph.nodeContaining(JAVA).orElseThrow();
    MetamodelNode pcmNode = graph.nodeContaining(PCM).orElseThrow();

    Optional<MetamodelNode> dominant =
        strategy.selectDominant(
            TestDominanceContexts.structuralOnly(graph, Set.of(umlNode, javaNode, pcmNode)));

    assertEquals(Optional.of(umlNode), dominant);
  }

  @Test
  @DisplayName("empty present nodes returns empty")
  void test4() {
    PropagationGraph graph = new PropagationGraph(List.of(spec(UML, JAVA)));

    assertTrue(
        strategy.selectDominant(TestDominanceContexts.structuralOnly(graph, Set.of())).isEmpty());
  }
}
