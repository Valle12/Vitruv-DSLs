package tools.vitruv.strategy;

import lombok.Getter;

@Getter
public enum StrategyType {
  EXPLICIT("explicit"),
  REACHABILITY("reachability"),
  DERIVATION_LOSS("derivationLoss");

  private final String type;

  StrategyType(String type) {
    this.type = type;
  }
}
