package tools.vitruv.dsls.reactions.migration.testspecs;

import java.util.Map;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import pcm_mockup.Component;
import pcm_mockup.PInterface;
import pcm_mockup.Pcm_mockupFactory;
import pcm_mockup.Pcm_mockupPackage;
import pcm_mockup.Repository;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.change.propagation.ConsistencyRuleId;
import tools.vitruv.change.propagation.ConsistencyRuleTrigger;
import tools.vitruv.change.propagation.ConsistencyRuleTrigger.ValueSlot;
import uml_mockup.UClass;
import uml_mockup.UInterface;
import uml_mockup.UMethod;
import uml_mockup.UPackage;
import uml_mockup.Uml_mockupPackage;

public final class SelectiveMockupSpecifications {
  public static final ConsistencyRuleId ROOT_INSERTED =
      ConsistencyRuleId.of("mockup::RootInserted");
  public static final ConsistencyRuleId ROOT_INSERTED_V2 =
      ConsistencyRuleId.of("mockup::RootInsertedV2");
  public static final ConsistencyRuleId CLASS_INSERTED =
      ConsistencyRuleId.of("mockup::ClassInserted");
  public static final ConsistencyRuleId CLASS_INSERTED_V2 =
      ConsistencyRuleId.of("mockup::ClassInsertedV2");
  public static final ConsistencyRuleId INTERFACE_INSERTED =
      ConsistencyRuleId.of("mockup::InterfaceInserted");
  public static final ConsistencyRuleId INTERFACE_INSERTED_V2 =
      ConsistencyRuleId.of("mockup::InterfaceInsertedV2");
  public static final ConsistencyRuleId METHOD_INSERTED =
      ConsistencyRuleId.of("mockup::MethodInserted");
  public static final ConsistencyRuleId BACK_COMPONENT_INSERTED =
      ConsistencyRuleId.of("mockupBack::ComponentInserted");
  public static final ConsistencyRuleId BACK_INTERFACE_INSERTED =
      ConsistencyRuleId.of("mockupBack::PInterfaceInserted");
  public static final ConsistencyRuleId BACK_INTERFACE_INSERTED_V2 =
      ConsistencyRuleId.of("mockupBack::PInterfaceInsertedV2");
  public static final String CLASS_HASH = "c1".repeat(32);
  public static final String CLASS_HASH_V2 = "c2".repeat(32);
  public static final String INTERFACE_HASH = "i1".repeat(32);
  public static final String INTERFACE_HASH_V2 = "i2".repeat(32);
  public static final String METHOD_HASH = "d1".repeat(32);
  public static final String ROOT_HASH = "e1".repeat(32);
  public static final String BACK_HASH = "f1".repeat(32);
  private static final String UML = Uml_mockupPackage.eNS_URI;
  public static final ConsistencyRuleTrigger ROOT_TRIGGER =
      new ConsistencyRuleTrigger(
          "InsertRootEObject", "", "", UML + "#UPackage", ValueSlot.NEW_VALUE);
  public static final ConsistencyRuleTrigger CLASS_TRIGGER =
      new ConsistencyRuleTrigger(
          "InsertEReference", UML + "#UPackage", "classes", UML + "#UClass", ValueSlot.NEW_VALUE);
  public static final ConsistencyRuleTrigger INTERFACE_TRIGGER =
      new ConsistencyRuleTrigger(
          "InsertEReference",
          UML + "#UPackage",
          "interfaces",
          UML + "#UInterface",
          ValueSlot.NEW_VALUE);
  public static final ConsistencyRuleTrigger METHOD_TRIGGER =
      new ConsistencyRuleTrigger(
          "InsertEReference",
          UML + "#UInterface",
          "methods",
          UML + "#UMethod",
          ValueSlot.NEW_VALUE);
  private static final String PCM = Pcm_mockupPackage.eNS_URI;
  public static final ConsistencyRuleTrigger BACK_COMPONENT_TRIGGER =
      new ConsistencyRuleTrigger(
          "InsertEReference",
          PCM + "#Repository",
          "components",
          PCM + "#Component",
          ValueSlot.NEW_VALUE);
  public static final ConsistencyRuleTrigger BACK_INTERFACE_TRIGGER =
      new ConsistencyRuleTrigger(
          "InsertEReference",
          PCM + "#Repository",
          "interfaces",
          PCM + "#PInterface",
          ValueSlot.NEW_VALUE);

  private SelectiveMockupSpecifications() {}

  public static ChangePropagationSpecification umlToPcm(
      Map<ConsistencyRuleId, String> ruleHashes,
      Map<ConsistencyRuleId, ConsistencyRuleTrigger> ruleTriggers) {
    return new RuleAwareSpecification(umlToPcmVariant("", true), ruleHashes, ruleTriggers);
  }

  public static ChangePropagationSpecification umlToPcmWithComponentMarker(
      String componentMarker,
      Map<ConsistencyRuleId, String> ruleHashes,
      Map<ConsistencyRuleId, ConsistencyRuleTrigger> ruleTriggers) {
    return new RuleAwareSpecification(
        umlToPcmVariant(componentMarker, true), ruleHashes, ruleTriggers);
  }

  public static ChangePropagationSpecification umlToPcmWithoutMethodMirroring(
      Map<ConsistencyRuleId, String> ruleHashes,
      Map<ConsistencyRuleId, ConsistencyRuleTrigger> ruleTriggers) {
    return new RuleAwareSpecification(umlToPcmVariant("", false), ruleHashes, ruleTriggers);
  }

  public static ChangePropagationSpecification pcmToUml(
      Map<ConsistencyRuleId, String> ruleHashes,
      Map<ConsistencyRuleId, ConsistencyRuleTrigger> ruleTriggers) {
    return new RuleAwareSpecification(
        UmlPcmMockupSpecifications.pcmToUml(), ruleHashes, ruleTriggers);
  }

  private static MirroringSpecification umlToPcmVariant(
      String componentMarker, boolean mirrorMethods) {
    return new MirroringSpecification(
        Uml_mockupPackage.eINSTANCE,
        Pcm_mockupPackage.eINSTANCE,
        UmlPcmMockupSpecifications.PCM_EXTENSION) {
      @Override
      protected EObject mirrorRoot(EObject sourceRoot) {
        return sourceRoot instanceof UPackage
            ? Pcm_mockupFactory.eINSTANCE.createRepository()
            : null;
      }

      @Override
      protected EObject mirrorChild(EObject mirrorParent, EObject sourceChild) {
        if (mirrorParent instanceof Repository repository) {
          if (sourceChild instanceof UClass) {
            Component component = Pcm_mockupFactory.eINSTANCE.createComponent();
            if (!componentMarker.isEmpty()) {
              component.setComponentExclusive(componentMarker);
            }

            repository.getComponents().add(component);
            return component;
          }
          if (sourceChild instanceof UInterface) {
            PInterface pInterface = Pcm_mockupFactory.eINSTANCE.createPInterface();
            repository.getInterfaces().add(pInterface);
            return pInterface;
          }
        }
        if (mirrorMethods
            && mirrorParent instanceof PInterface pInterface
            && sourceChild instanceof UMethod) {
          var method = Pcm_mockupFactory.eINSTANCE.createPMethod();
          pInterface.getMethods().add(method);
          return method;
        }

        return null;
      }

      @Override
      protected void syncAttribute(EObject source, EObject mirror, EAttribute attribute) {
        copyAttribute(source, mirror, attribute, attribute.getName());
      }
    };
  }
}
