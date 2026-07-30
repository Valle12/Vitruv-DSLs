package tools.vitruv.dsls.reactions.migration.preservation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LossReason {
  NO_COUNTERPART_RESOURCE("the model file has no counterpart after the migration"),
  NO_COUNTERPART_CONTAINER("the element containing it is no longer derived"),
  NO_COMPATIBLE_FEATURE("the counterpart metaclass has no feature that can hold it"),
  AMBIGUOUS_FEATURE("several features of the counterpart metaclass could hold it"),
  AMBIGUOUS_COUNTERPART("several migrated elements could be the same one and nobody chose"),
  SLOT_OCCUPIED("the counterpart feature is single-valued and the new rules already set it"),
  UPPER_BOUND_REACHED("the counterpart feature cannot hold any more values"),
  UNRESOLVABLE_REFERENCE("it references an element that no longer exists"),
  VALIDATION_REJECTED("re-attaching it made the model invalid"),
  RULE_NO_LONGER_DERIVES("the new consistency rules no longer derive it");

  private final String description;
}
