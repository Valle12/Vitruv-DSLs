package tools.vitruv.strategy;

import java.util.Optional;
import java.util.Set;

public interface DominanceStrategy {
  Optional<Set<String>> selectDominant(DominanceContext context);
}
