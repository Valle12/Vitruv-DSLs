package tools.vitruv.dsls.reactions.migration.spec;

import java.util.List;
import tools.vitruv.change.propagation.ChangePropagationSpecification;

/** Supplies the change propagation specifications a migration is to propagate with. */
@FunctionalInterface
public interface SpecificationSource {
  /**
   * Creates a fresh set of specifications. A specification keeps state from the propagation it
   * took part in, so every migration asks for its own.
   *
   * @return the newly created specifications
   */
  List<ChangePropagationSpecification> createSpecifications();
}
