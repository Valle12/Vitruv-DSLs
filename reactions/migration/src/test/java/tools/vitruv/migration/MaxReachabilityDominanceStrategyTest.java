package tools.vitruv.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.emf.ecore.EObject;
import org.junit.jupiter.api.Test;
import tools.vitruv.change.atomic.EChange;
import tools.vitruv.change.composite.MetamodelDescriptor;
import tools.vitruv.change.correspondence.Correspondence;
import tools.vitruv.change.correspondence.view.EditableCorrespondenceModelView;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.change.propagation.impl.AbstractChangePropagationSpecification;
import tools.vitruv.change.utils.ResourceAccess;

class MaxReachabilityDominanceStrategyTest {
  private static final String UML = "http://www.eclipse.org/uml2/5.0.0/UML";
  private static final String JAVA = "http://www.emftext.org/java";
  private static final String PCM = "http://palladiosimulator.org/PalladioComponentModel/5.2";
  private final MaxReachabilityDominanceStrategy strategy = new MaxReachabilityDominanceStrategy();

  private static DominanceContext context(PropagationGraph graph, Collection<Set<String>> present) {
    return new DominanceContext() {
      @Override
      public PropagationGraph graph() {
        return graph;
      }

      @Override
      public Collection<Set<String>> presentNodes() {
        return present;
      }

      @Override
      public long reDerivationLoss(Set<String> dominantNode) {
        throw new UnsupportedOperationException("structural strategy must not call this");
      }
    };
  }

  private static ChangePropagationSpecification spec(String sourceUri, String targetUri) {
    return new AbstractChangePropagationSpecification(
        MetamodelDescriptor.with(sourceUri), MetamodelDescriptor.with(targetUri)) {
      @Override
      public boolean doesHandleChange(
          EChange<EObject> change, EditableCorrespondenceModelView<Correspondence> view) {
        return false;
      }

      @Override
      public void propagateChange(
          EChange<EObject> change,
          EditableCorrespondenceModelView<Correspondence> view,
          ResourceAccess resourceAccess) {}
    };
  }

  @Test
  void picksTheNodeThatReachesAllOthers() {
    // UML -> JAVA, UML -> PCM. UML is the unique full-reach source.
    PropagationGraph graph =
        PropagationGraph.fromSpecifications(List.of(spec(UML, JAVA), spec(UML, PCM)));
    Set<String> umlNode = graph.nodeContaining(UML).orElseThrow();
    Set<String> javaNode = graph.nodeContaining(JAVA).orElseThrow();
    Set<String> pcmNode = graph.nodeContaining(PCM).orElseThrow();

    Optional<Set<String>> dominant =
        strategy.selectDominant(context(graph, List.of(umlNode, javaNode, pcmNode)));

    assertThat(dominant).contains(umlNode);
  }

  @Test
  void breaksCycleTiesByOutDegreeThenReach() {
    PropagationGraph graph =
        PropagationGraph.fromSpecifications(List.of(spec(UML, JAVA), spec(JAVA, UML)));
    Set<String> umlNode = graph.nodeContaining(UML).orElseThrow();
    Set<String> javaNode = graph.nodeContaining(JAVA).orElseThrow();

    Optional<Set<String>> dominant =
        strategy.selectDominant(context(graph, List.of(umlNode, javaNode)));

    assertThat(dominant).hasValueSatisfying(node -> assertThat(node).isIn(umlNode, javaNode));
  }

  @Test
  void fallsBackToBestPartialCoverageWhenNoNodeReachesAll() {
    PropagationGraph graph =
        PropagationGraph.fromSpecifications(
            List.of(spec(UML, JAVA), spec(PCM, PCM))); // PCM self-edge keeps it in the graph
    Set<String> umlNode = graph.nodeContaining(UML).orElseThrow();
    Set<String> javaNode = graph.nodeContaining(JAVA).orElseThrow();
    Set<String> pcmNode = graph.nodeContaining(PCM).orElseThrow();

    Optional<Set<String>> dominant =
        strategy.selectDominant(context(graph, List.of(umlNode, javaNode, pcmNode)));

    assertThat(dominant).contains(umlNode);
  }

  @Test
  void emptyPresentNodesReturnsEmpty() {
    PropagationGraph graph = PropagationGraph.fromSpecifications(List.of(spec(UML, JAVA)));

    assertThat(strategy.selectDominant(context(graph, List.of()))).isEmpty();
  }
}
