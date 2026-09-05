package tools.vitruv.dsls.reactions.migration.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tools.vitruv.dsls.reactions.migration.TestSpecifications.FAMILIES;
import static tools.vitruv.dsls.reactions.migration.TestSpecifications.JAVA;
import static tools.vitruv.dsls.reactions.migration.TestSpecifications.PCM;
import static tools.vitruv.dsls.reactions.migration.TestSpecifications.PERSONS;
import static tools.vitruv.dsls.reactions.migration.TestSpecifications.UML;
import static tools.vitruv.dsls.reactions.migration.TestSpecifications.spec;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.vitruv.dsls.reactions.migration.TestDominanceContexts;
import tools.vitruv.dsls.reactions.migration.strategy.DominanceContext;
import tools.vitruv.dsls.reactions.migration.strategy.ExplicitDominance;
import tools.vitruv.dsls.reactions.migration.strategy.MaxReachabilityDominance;

class MigrationPlannerTest {
  private final MaxReachabilityDominance reachability = new MaxReachabilityDominance();

  private static DominanceContext context(PropagationGraph graph, Set<MetamodelNode> present) {
    return TestDominanceContexts.structuralOnly(graph, present);
  }

  private static Set<MetamodelNode> present(PropagationGraph graph, String... nsUris) {
    Set<MetamodelNode> nodes = new LinkedHashSet<>();
    for (String nsUri : nsUris) {
      nodes.add(graph.nodeContaining(nsUri).orElseThrow());
    }
    return nodes;
  }

  private static void assertCoversEverything(
      PropagationGraph graph, List<MetamodelNode> sources, Set<MetamodelNode> present) {
    for (MetamodelNode node : present) {
      boolean isSource = sources.contains(node);
      boolean reachableFromSource =
          sources.stream().anyMatch(source -> graph.reachableFrom(source).contains(node));
      assertTrue(
          isSource || reachableFromSource,
          () -> "present node " + node + " is neither a source nor reachable from " + sources);
    }
  }

  @Test
  @DisplayName("single hub dominant covers all three metamodels")
  void test1() {
    PropagationGraph graph =
        new PropagationGraph(
            List.of(spec(UML, JAVA), spec(JAVA, UML), spec(UML, PCM), spec(PCM, UML)));
    MetamodelNode uml = graph.nodeContaining(UML).orElseThrow();
    Set<MetamodelNode> present = present(graph, UML, JAVA, PCM);

    DominancePlan plan = new MigrationPlanner(graph, reachability).plan(context(graph, present));

    assertEquals(List.of(uml), plan.sources(), "the hub alone reaches every other model");
    assertEquals(2, plan.derived().size(), "Java and PCM are re-derived from the hub");
    assertCoversEverything(graph, plan.sources(), present);
  }

  @Test
  @DisplayName("unchanged for two fully wired metamodels")
  void test2() {
    PropagationGraph graph = new PropagationGraph(List.of(spec(UML, JAVA), spec(JAVA, UML)));
    Set<MetamodelNode> present = present(graph, UML, JAVA);

    DominancePlan plan = new MigrationPlanner(graph, reachability).plan(context(graph, present));

    assertEquals(1, plan.sources().size(), "one dominant source for two wired metamodels");
    assertEquals(1, plan.derived().size(), "the other metamodel is derived");
    assertCoversEverything(graph, plan.sources(), present);
  }

  @Test
  @DisplayName("disconnected clusters each contribute a source")
  void test3() {
    PropagationGraph graph =
        new PropagationGraph(
            List.of(
                spec(UML, JAVA), spec(JAVA, UML),
                spec(PCM, FAMILIES), spec(FAMILIES, PCM)));
    Set<MetamodelNode> present = present(graph, UML, JAVA, PCM, FAMILIES);

    DominancePlan plan = new MigrationPlanner(graph, reachability).plan(context(graph, present));

    assertEquals(2, plan.sources().size(), "one source per disconnected cluster");
    assertEquals(2, plan.derived().size(), "the other member of each cluster is derived");
    assertCoversEverything(graph, plan.sources(), present);
  }

  @Test
  @DisplayName("orphan metamodel without reactions becomes its own source")
  void test4() {
    PropagationGraph graph =
        new PropagationGraph(List.of(spec(UML, JAVA), spec(JAVA, UML), spec(PERSONS, PERSONS)));
    MetamodelNode persons = graph.nodeContaining(PERSONS).orElseThrow();
    Set<MetamodelNode> present = present(graph, UML, JAVA, PERSONS);

    DominancePlan plan = new MigrationPlanner(graph, reachability).plan(context(graph, present));

    assertTrue(plan.sources().contains(persons), "the orphan must be preserved as a source");
    assertCoversEverything(graph, plan.sources(), present);
  }

  @Test
  @DisplayName("two independent sources feeding a shared target")
  void test5() {
    PropagationGraph graph = new PropagationGraph(List.of(spec(UML, JAVA), spec(PCM, JAVA)));
    MetamodelNode uml = graph.nodeContaining(UML).orElseThrow();
    MetamodelNode pcm = graph.nodeContaining(PCM).orElseThrow();
    MetamodelNode java = graph.nodeContaining(JAVA).orElseThrow();
    Set<MetamodelNode> present = present(graph, UML, JAVA, PCM);

    DominancePlan plan = new MigrationPlanner(graph, reachability).plan(context(graph, present));

    assertEquals(Set.of(uml, pcm), Set.copyOf(plan.sources()), "both independent sources are kept");
    assertEquals(List.of(java), plan.derived(), "only the shared target is derived");
    assertCoversEverything(graph, plan.sources(), present);
  }

  @Test
  @DisplayName("chain dominant reaches all transitively")
  void test6() {
    PropagationGraph graph = new PropagationGraph(List.of(spec(UML, JAVA), spec(JAVA, PCM)));
    MetamodelNode uml = graph.nodeContaining(UML).orElseThrow();
    Set<MetamodelNode> present = present(graph, UML, JAVA, PCM);

    DominancePlan plan = new MigrationPlanner(graph, reachability).plan(context(graph, present));

    assertEquals(List.of(uml), plan.sources(), "the chain head covers the whole chain");
    assertCoversEverything(graph, plan.sources(), present);
  }

  @Test
  @DisplayName("chain tail forces a second source")
  void test7() {
    PropagationGraph graph = new PropagationGraph(List.of(spec(UML, JAVA), spec(JAVA, PCM)));
    MetamodelNode pcm = graph.nodeContaining(PCM).orElseThrow();
    Set<MetamodelNode> present = present(graph, UML, JAVA, PCM);

    DominancePlan plan =
        new MigrationPlanner(graph, new ExplicitDominance("Palladio"))
            .plan(context(graph, present));

    assertSame(pcm, plan.dominant(), "the explicit token pins PCM as dominant");
    assertCoversEverything(graph, plan.sources(), present);
    assertTrue(
        plan.sources().size() >= 2, "a sink dominant cannot cover the models upstream of it");
  }

  @Test
  @DisplayName("dominant is always the first source")
  void test8() {
    PropagationGraph graph =
        new PropagationGraph(
            List.of(spec(UML, JAVA), spec(JAVA, UML), spec(UML, PCM), spec(PCM, UML)));
    Set<MetamodelNode> present = present(graph, UML, JAVA, PCM);

    DominancePlan plan = new MigrationPlanner(graph, reachability).plan(context(graph, present));

    assertSame(plan.sources().getFirst(), plan.dominant(), "the dominant heads the source order");
  }
}
