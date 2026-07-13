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

class FewestChangesDominanceTest {
  private final FewestChangesDominance strategy = new FewestChangesDominance();

  @Test
  @DisplayName("picks the candidate with the fewest trial changes")
  void test1() {
    PropagationGraph graph = new PropagationGraph(List.of(spec(UML, JAVA), spec(JAVA, UML)));
    MetamodelNode umlNode = graph.nodeContaining(UML).orElseThrow();
    MetamodelNode javaNode = graph.nodeContaining(JAVA).orElseThrow();

    Optional<MetamodelNode> dominant =
        strategy.selectDominant(
            TestDominanceContexts.withTrialCounts(
                graph, Set.of(umlNode, javaNode), candidate -> candidate.equals(umlNode) ? 3 : 12));

    assertEquals(Optional.of(umlNode), dominant);
  }

  @Test
  @DisplayName("only candidates reaching all present nodes are tried")
  void test2() {
    PropagationGraph graph = new PropagationGraph(List.of(spec(UML, JAVA), spec(JAVA, PCM)));
    MetamodelNode umlNode = graph.nodeContaining(UML).orElseThrow();
    MetamodelNode javaNode = graph.nodeContaining(JAVA).orElseThrow();
    MetamodelNode pcmNode = graph.nodeContaining(PCM).orElseThrow();

    Optional<MetamodelNode> dominant =
        strategy.selectDominant(
            TestDominanceContexts.withTrialCounts(
                graph,
                Set.of(umlNode, javaNode, pcmNode),
                candidate -> {
                  assertEquals(umlNode, candidate, "only the chain head reaches everything");
                  return 7;
                }));

    assertEquals(Optional.of(umlNode), dominant);
  }

  @Test
  @DisplayName("empty when no candidate reaches all")
  void test3() {
    PropagationGraph graph = new PropagationGraph(List.of(spec(UML, JAVA), spec(PCM, PCM)));
    MetamodelNode umlNode = graph.nodeContaining(UML).orElseThrow();
    MetamodelNode javaNode = graph.nodeContaining(JAVA).orElseThrow();
    MetamodelNode pcmNode = graph.nodeContaining(PCM).orElseThrow();

    assertTrue(
        strategy
            .selectDominant(
                TestDominanceContexts.withTrialCounts(
                    graph, Set.of(umlNode, javaNode, pcmNode), candidate -> 0))
            .isEmpty());
  }

  @Test
  @DisplayName("failing trial skips the candidate instead of aborting")
  void test4() {
    PropagationGraph graph = new PropagationGraph(List.of(spec(UML, JAVA), spec(JAVA, UML)));
    MetamodelNode umlNode = graph.nodeContaining(UML).orElseThrow();
    MetamodelNode javaNode = graph.nodeContaining(JAVA).orElseThrow();

    Optional<MetamodelNode> dominant =
        strategy.selectDominant(
            TestDominanceContexts.withTrialCounts(
                graph,
                Set.of(umlNode, javaNode),
                candidate -> {
                  if (candidate.equals(umlNode)) {
                    throw new IllegalStateException("trial exploded");
                  }

                  return 5;
                }));

    assertEquals(Optional.of(javaNode), dominant);
  }

  @Test
  @DisplayName("empty when every trial fails")
  void test5() {
    PropagationGraph graph = new PropagationGraph(List.of(spec(UML, JAVA), spec(JAVA, UML)));
    MetamodelNode umlNode = graph.nodeContaining(UML).orElseThrow();
    MetamodelNode javaNode = graph.nodeContaining(JAVA).orElseThrow();

    assertTrue(
        strategy
            .selectDominant(
                TestDominanceContexts.withTrialCounts(
                    graph,
                    Set.of(umlNode, javaNode),
                    candidate -> {
                      throw new IllegalStateException("no trial completes");
                    }))
            .isEmpty());
  }
}
