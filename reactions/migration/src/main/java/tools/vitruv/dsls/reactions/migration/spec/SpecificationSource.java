package tools.vitruv.dsls.reactions.migration.spec;

import java.util.List;
import tools.vitruv.change.propagation.ChangePropagationSpecification;

@FunctionalInterface
public interface SpecificationSource {
  List<ChangePropagationSpecification> createSpecifications();
}
