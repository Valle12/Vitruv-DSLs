package tools.vitruv.dsls.reactions.migration.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StrategyChoiceTest {
  @Test
  @DisplayName("parses all documented tokens")
  void test1() {
    assertEquals(Optional.of(StrategyChoice.EXPLICIT), StrategyChoice.fromToken("explicit"));
    assertEquals(
        Optional.of(StrategyChoice.REACHABILITY), StrategyChoice.fromToken("reachability"));
    assertEquals(
        Optional.of(StrategyChoice.FEWEST_CHANGES), StrategyChoice.fromToken("derivationLoss"));
    assertEquals(
        Optional.of(StrategyChoice.FEWEST_CHANGES), StrategyChoice.fromToken("fewestChanges"));
  }

  @Test
  @DisplayName("parsing is case insensitive")
  void test2() {
    assertEquals(Optional.of(StrategyChoice.EXPLICIT), StrategyChoice.fromToken("EXPLICIT"));
    assertEquals(
        Optional.of(StrategyChoice.FEWEST_CHANGES), StrategyChoice.fromToken("derivationloss"));
  }

  @Test
  @DisplayName("unknown token is empty")
  void test3() {
    assertTrue(StrategyChoice.fromToken("bogus").isEmpty());
  }
}
