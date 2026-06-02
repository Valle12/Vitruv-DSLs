package tools.vitruv.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ReachableNodesTest {
  @Test
  void containsReturnsTrueForMember() {
    Set<String> nodeA = Set.of("a");
    Set<String> nodeB = Set.of("b");
    ReachableNodes reachable = new ReachableNodes(Set.of(nodeA, nodeB));

    assertThat(reachable.contains(nodeA)).isTrue();
    assertThat(reachable.contains(nodeB)).isTrue();
  }

  @Test
  void containsReturnsFalseForNonMember() {
    ReachableNodes reachable = new ReachableNodes(Set.of(Set.of("a")));

    assertThat(reachable.contains(Set.of("missing"))).isFalse();
  }

  @Test
  void emptyContainsNothing() {
    assertThat(ReachableNodes.EMPTY.contains(Set.of("a"))).isFalse();
    assertThat(ReachableNodes.EMPTY.reachableNodes()).isEmpty();
  }

  @Test
  void recordEquality() {
    ReachableNodes a = new ReachableNodes(Set.of(Set.of("x"), Set.of("y")));
    ReachableNodes b = new ReachableNodes(Set.of(Set.of("y"), Set.of("x")));

    assertThat(a).isEqualTo(b);
    assertThat(a).hasSameHashCodeAs(b);
  }
}
