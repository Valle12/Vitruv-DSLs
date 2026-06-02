package tools.vitruv.migration;

import static org.assertj.core.api.Assertions.assertThat;

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

class PropagationGraphTest {
  private static final String UML = "http://www.eclipse.org/uml2/5.0.0/UML";
  private static final String JAVA = "http://www.emftext.org/java";
  private static final String PCM = "http://palladiosimulator.org/PalladioComponentModel/5.2";

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

  @SuppressWarnings("unused")
  private static Optional<Set<String>> typeCheck(PropagationGraph graph) {
    return graph.nodeContaining(UML);
  }

  @Test
  void fromSpecificationsRegistersBothEndpoints() {
    PropagationGraph graph = PropagationGraph.fromSpecifications(List.of(spec(UML, JAVA)));

    assertThat(graph.nodeContaining(UML)).isPresent();
    assertThat(graph.nodeContaining(JAVA)).isPresent();
  }

  @Test
  void outDegreeCountsDirectNeighbors() {
    PropagationGraph graph =
        PropagationGraph.fromSpecifications(
            List.of(spec(UML, JAVA), spec(UML, PCM), spec(JAVA, UML)));

    Set<String> umlNode = graph.nodeContaining(UML).orElseThrow();
    Set<String> javaNode = graph.nodeContaining(JAVA).orElseThrow();
    Set<String> pcmNode = graph.nodeContaining(PCM).orElseThrow();

    assertThat(graph.outDegree(umlNode)).isEqualTo(2);
    assertThat(graph.outDegree(javaNode)).isEqualTo(1);
    assertThat(graph.outDegree(pcmNode)).isZero();
  }

  @Test
  void reachableFromComputesTransitiveClosure() {
    // UML -> JAVA -> PCM
    PropagationGraph graph =
        PropagationGraph.fromSpecifications(List.of(spec(UML, JAVA), spec(JAVA, PCM)));

    Set<String> umlNode = graph.nodeContaining(UML).orElseThrow();
    Set<String> javaNode = graph.nodeContaining(JAVA).orElseThrow();
    Set<String> pcmNode = graph.nodeContaining(PCM).orElseThrow();

    ReachableNodes fromUml = graph.reachableFrom(umlNode);
    assertThat(fromUml.contains(javaNode)).isTrue();
    assertThat(fromUml.contains(pcmNode)).isTrue();
    assertThat(fromUml.reachableNodes()).hasSize(2);

    // PCM is a sink, reaches nothing.
    assertThat(graph.reachableFrom(pcmNode).reachableNodes()).isEmpty();
  }

  @Test
  void reachableFromHandlesCyclesWithoutInfiniteLoop() {
    // UML <-> JAVA cycle. reachableFrom(UML) must include JAVA, and self (because the cycle
    // brings us back).
    PropagationGraph graph =
        PropagationGraph.fromSpecifications(List.of(spec(UML, JAVA), spec(JAVA, UML)));

    Set<String> umlNode = graph.nodeContaining(UML).orElseThrow();
    Set<String> javaNode = graph.nodeContaining(JAVA).orElseThrow();

    ReachableNodes fromUml = graph.reachableFrom(umlNode);
    assertThat(fromUml.contains(javaNode)).isTrue();
    assertThat(fromUml.contains(umlNode)).isTrue();
  }

  @Test
  void reachesAllTrueWhenSingleSourceDominates() {
    // UML -> JAVA and UML -> PCM: UML reaches everything else.
    PropagationGraph graph =
        PropagationGraph.fromSpecifications(List.of(spec(UML, JAVA), spec(UML, PCM)));

    Set<String> umlNode = graph.nodeContaining(UML).orElseThrow();
    Set<String> javaNode = graph.nodeContaining(JAVA).orElseThrow();
    Set<String> pcmNode = graph.nodeContaining(PCM).orElseThrow();

    assertThat(graph.reachesAll(umlNode, List.of(umlNode, javaNode, pcmNode))).isTrue();
    // JAVA is a sink here, can't reach UML or PCM.
    assertThat(graph.reachesAll(javaNode, List.of(umlNode, javaNode, pcmNode))).isFalse();
  }

  @Test
  void nodeContainingReturnsEmptyOptionalForUnknownUri() {
    PropagationGraph graph = PropagationGraph.fromSpecifications(List.of(spec(UML, JAVA)));

    assertThat(graph.nodeContaining("http://does/not/exist")).isEmpty();
  }

  @Test
  void emptySpecificationsProducesEmptyGraph() {
    PropagationGraph graph = PropagationGraph.fromSpecifications(List.of());

    assertThat(graph.nodeContaining(UML)).isEmpty();
    assertThat(graph.toString()).startsWith("propagation graph:");
  }
}
