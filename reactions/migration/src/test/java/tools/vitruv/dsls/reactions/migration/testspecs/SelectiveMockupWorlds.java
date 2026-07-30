package tools.vitruv.dsls.reactions.migration.testspecs;

import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.BACK_COMPONENT_INSERTED;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.BACK_COMPONENT_TRIGGER;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.BACK_HASH;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.CLASS_HASH;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.CLASS_HASH_V2;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.CLASS_INSERTED;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.CLASS_INSERTED_V2;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.CLASS_TRIGGER;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.INTERFACE_HASH;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.INTERFACE_HASH_V2;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.INTERFACE_INSERTED;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.INTERFACE_INSERTED_V2;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.INTERFACE_TRIGGER;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.METHOD_HASH;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.METHOD_INSERTED;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.METHOD_TRIGGER;

import java.util.List;
import java.util.Map;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.change.propagation.ConsistencyRuleId;
import tools.vitruv.change.propagation.ConsistencyRuleTrigger;
import tools.vitruv.dsls.reactions.migration.spec.SpecificationSource;

public final class SelectiveMockupWorlds {
  private SelectiveMockupWorlds() {}

  public static SpecificationSource version1() {
    return world(version1Hashes(), version1Triggers(), true);
  }

  public static SpecificationSource forwardVersion1() {
    return world(version1Hashes(), version1Triggers(), false);
  }

  public static SpecificationSource forwardWithRenamedInterfaceRule() {
    return world(renamedInterfaceHashes(), renamedInterfaceTriggers(), false);
  }

  public static SpecificationSource forwardWithoutMethodRule() {
    return () ->
        List.of(
            SelectiveMockupSpecifications.umlToPcmWithoutMethodMirroring(
                Map.of(CLASS_INSERTED, CLASS_HASH, INTERFACE_INSERTED, INTERFACE_HASH),
                Map.of(CLASS_INSERTED, CLASS_TRIGGER, INTERFACE_INSERTED, INTERFACE_TRIGGER)));
  }

  private static Map<ConsistencyRuleId, String> version1Hashes() {
    return Map.of(
        CLASS_INSERTED, CLASS_HASH,
        INTERFACE_INSERTED, INTERFACE_HASH,
        METHOD_INSERTED, METHOD_HASH);
  }

  private static Map<ConsistencyRuleId, ConsistencyRuleTrigger> version1Triggers() {
    return Map.of(
        CLASS_INSERTED, CLASS_TRIGGER,
        INTERFACE_INSERTED, INTERFACE_TRIGGER,
        METHOD_INSERTED, METHOD_TRIGGER);
  }

  public static SpecificationSource withRenamedClassRule() {
    return world(
        Map.of(
            CLASS_INSERTED_V2, CLASS_HASH_V2,
            INTERFACE_INSERTED, INTERFACE_HASH,
            METHOD_INSERTED, METHOD_HASH),
        Map.of(
            CLASS_INSERTED_V2, CLASS_TRIGGER,
            INTERFACE_INSERTED, INTERFACE_TRIGGER,
            METHOD_INSERTED, METHOD_TRIGGER),
        true);
  }

  public static SpecificationSource withEditedClassRule() {
    return () ->
        List.of(
            SelectiveMockupSpecifications.umlToPcmWithComponentMarker(
                "v2",
                Map.of(
                    CLASS_INSERTED, CLASS_HASH_V2,
                    INTERFACE_INSERTED, INTERFACE_HASH,
                    METHOD_INSERTED, METHOD_HASH),
                Map.of(
                    CLASS_INSERTED, CLASS_TRIGGER,
                    INTERFACE_INSERTED, INTERFACE_TRIGGER,
                    METHOD_INSERTED, METHOD_TRIGGER)),
            backward());
  }

  public static SpecificationSource withoutMethodRule() {
    return () ->
        List.of(
            SelectiveMockupSpecifications.umlToPcmWithoutMethodMirroring(
                Map.of(CLASS_INSERTED, CLASS_HASH, INTERFACE_INSERTED, INTERFACE_HASH),
                Map.of(CLASS_INSERTED, CLASS_TRIGGER, INTERFACE_INSERTED, INTERFACE_TRIGGER)),
            backward());
  }

  public static SpecificationSource withRenamedInterfaceRule() {
    return world(renamedInterfaceHashes(), renamedInterfaceTriggers(), true);
  }

  private static Map<ConsistencyRuleId, String> renamedInterfaceHashes() {
    return Map.of(
        CLASS_INSERTED, CLASS_HASH,
        INTERFACE_INSERTED_V2, INTERFACE_HASH_V2,
        METHOD_INSERTED, METHOD_HASH);
  }

  private static Map<ConsistencyRuleId, ConsistencyRuleTrigger> renamedInterfaceTriggers() {
    return Map.of(
        CLASS_INSERTED, CLASS_TRIGGER,
        INTERFACE_INSERTED_V2, INTERFACE_TRIGGER,
        METHOD_INSERTED, METHOD_TRIGGER);
  }

  private static SpecificationSource world(
      Map<ConsistencyRuleId, String> ruleHashes,
      Map<ConsistencyRuleId, ConsistencyRuleTrigger> ruleTriggers,
      boolean bidirectional) {
    return () -> {
      ChangePropagationSpecification forward =
          SelectiveMockupSpecifications.umlToPcm(ruleHashes, ruleTriggers);
      return bidirectional ? List.of(forward, backward()) : List.of(forward);
    };
  }

  private static ChangePropagationSpecification backward() {
    return SelectiveMockupSpecifications.pcmToUml(
        Map.of(BACK_COMPONENT_INSERTED, BACK_HASH),
        Map.of(BACK_COMPONENT_INSERTED, BACK_COMPONENT_TRIGGER));
  }
}
