package tools.vitruv.migration;

import java.util.Optional;
import java.util.Set;

public interface DominanceStrategy {
  Optional<Set<String>> selectDominant(DominanceContext context);
}
