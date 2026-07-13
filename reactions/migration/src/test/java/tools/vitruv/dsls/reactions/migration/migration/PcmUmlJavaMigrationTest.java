package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.vitruv.applications.pcmumlclass.CombinedPcmToUmlClassReactionsChangePropagationSpecification;
import tools.vitruv.applications.pcmumlclass.CombinedUmlClassToPcmReactionsChangePropagationSpecification;
import tools.vitruv.applications.umljava.JavaToUmlChangePropagationSpecification;
import tools.vitruv.applications.umljava.UmlToJavaChangePropagationSpecification;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.dsls.reactions.migration.TestDominanceContexts;
import tools.vitruv.dsls.reactions.migration.graph.DominancePlan;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;
import tools.vitruv.dsls.reactions.migration.graph.MigrationPlanner;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;
import tools.vitruv.dsls.reactions.migration.strategy.DominanceStrategy;
import tools.vitruv.dsls.reactions.migration.strategy.ExplicitDominance;
import tools.vitruv.dsls.reactions.migration.strategy.MaxReachabilityDominance;

class PcmUmlJavaMigrationTest {
  private static final String UML = "http://www.eclipse.org/uml2/5.0.0/UML";
  private static final String JAVA = "http://www.emftext.org/java";
  private static final String PCM = "http://palladiosimulator.org/PalladioComponentModel/5.2";

  private static final List<ChangePropagationSpecification> UML_JAVA_PCM =
      List.of(
          new UmlToJavaChangePropagationSpecification(),
          new JavaToUmlChangePropagationSpecification(),
          new CombinedUmlClassToPcmReactionsChangePropagationSpecification(),
          new CombinedPcmToUmlClassReactionsChangePropagationSpecification());

  @Test
  @DisplayName("real UML-Java and PCM specs form a three-metamodel graph")
  void test1() {
    PropagationGraph graph = new PropagationGraph(UML_JAVA_PCM);

    assertTrue(graph.nodeContaining(UML).isPresent(), "UML must be a node");
    assertTrue(graph.nodeContaining(JAVA).isPresent(), "Java must be a node");
    assertTrue(graph.nodeContaining(PCM).isPresent(), "PCM must be a node");

    MetamodelNode uml = graph.nodeContaining(UML).orElseThrow();
    assertTrue(graph.reachableFrom(uml).contains(graph.nodeContaining(JAVA).orElseThrow()));
    assertTrue(graph.reachableFrom(uml).contains(graph.nodeContaining(PCM).orElseThrow()));
  }

  @Test
  @DisplayName("UML dominant covers Java and PCM in one plan")
  void test2() {
    PropagationGraph graph = new PropagationGraph(UML_JAVA_PCM);
    MetamodelNode uml = graph.nodeContaining(UML).orElseThrow();
    Set<MetamodelNode> present =
        Set.of(
            uml, graph.nodeContaining(JAVA).orElseThrow(), graph.nodeContaining(PCM).orElseThrow());

    for (DominanceStrategy strategy :
        List.of(new MaxReachabilityDominance(), new ExplicitDominance("uml"))) {
      DominancePlan plan =
          new MigrationPlanner(graph, strategy)
              .plan(TestDominanceContexts.structuralOnly(graph, present));
      assertEquals(List.of(uml), plan.sources(), "UML alone covers Java and PCM");
      assertEquals(2, plan.derived().size(), "Java and PCM are the re-derived sides");
    }
  }
}
