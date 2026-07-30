package tools.vitruv.dsls.reactions.migration.vsum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.emf.common.util.URI;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.change.propagation.ConsistencyRuleId;
import tools.vitruv.change.propagation.ConsistencyRuleTrigger;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.interaction.DetectingUserInteraction;
import tools.vitruv.dsls.reactions.migration.testspecs.RuleAwareSpecification;
import tools.vitruv.dsls.reactions.migration.testspecs.UmlPcmMockupSpecifications;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;
import uml_mockup.UPackage;
import uml_mockup.Uml_mockupFactory;
import uml_mockup.Uml_mockupPackage;

@ExtendWith(RegisterMetamodelsInStandalone.class)
class PersistedRulesTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();
  private static final ConsistencyRuleId UML_TO_PCM_RULE = ConsistencyRuleId.of("mockup::umlToPcm");
  private static final ConsistencyRuleId PCM_TO_UML_RULE = ConsistencyRuleId.of("mockup::pcmToUml");
  private static final String UML_TO_PCM_HASH = "a".repeat(64);
  private static final String PCM_TO_UML_HASH = "b".repeat(64);
  private static final ConsistencyRuleTrigger UML_TO_PCM_TRIGGER =
      new ConsistencyRuleTrigger(
          "InsertRootEObject",
          "",
          "",
          Uml_mockupPackage.eNS_URI + "#UPackage",
          ConsistencyRuleTrigger.ValueSlot.NEW_VALUE);

  @TempDir Path tempDir;

  @BeforeAll
  static void prepareStandalone() {
    Vsums.prepareStandalone(ADAPTERS);
  }

  @Test
  @DisplayName("propagation persists rule hashes and triggers that load returns")
  void test1() throws Exception {
    Path folder =
        seededVsum(
            "with-hashes",
            List.of(
                new RuleAwareSpecification(
                    UmlPcmMockupSpecifications.umlToPcm(),
                    Map.of(UML_TO_PCM_RULE, UML_TO_PCM_HASH),
                    Map.of(UML_TO_PCM_RULE, UML_TO_PCM_TRIGGER)),
                new RuleAwareSpecification(
                    UmlPcmMockupSpecifications.pcmToUml(),
                    Map.of(PCM_TO_UML_RULE, PCM_TO_UML_HASH),
                    Map.of())));

    Path registryFile =
        folder.resolve("consistencymetadata").resolve("vitruv").resolve("rule-hashes.txt");
    assertTrue(
        Files.exists(registryFile),
        "the registry must be persisted in the consistency metadata folder at " + registryFile);
    PersistedRules loaded = PersistedRules.load(folder);
    assertEquals(
        Map.of(UML_TO_PCM_RULE, UML_TO_PCM_HASH, PCM_TO_UML_RULE, PCM_TO_UML_HASH),
        loaded.hashes());
    assertEquals(Map.of(UML_TO_PCM_RULE, UML_TO_PCM_TRIGGER), loaded.triggers());
  }

  @Test
  @DisplayName("load returns empty maps if no registry was persisted")
  void test2() throws Exception {
    Path folder =
        seededVsum(
            "without-hashes",
            List.of(UmlPcmMockupSpecifications.umlToPcm(), UmlPcmMockupSpecifications.pcmToUml()));

    assertFalse(
        Files.exists(folder.resolve("consistencymetadata").resolve("vitruv")),
        "specifications without rule hashes must not persist a registry");
    assertEquals(Map.of(), PersistedRules.load(folder).hashes());
    assertEquals(Map.of(), PersistedRules.load(folder).triggers());
  }

  @Test
  @DisplayName("duplicate rule ids collapse to a single persisted entry")
  void test3() throws Exception {
    Path folder =
        seededVsum(
            "duplicate-ids",
            List.of(
                new RuleAwareSpecification(
                    UmlPcmMockupSpecifications.umlToPcm(),
                    Map.of(UML_TO_PCM_RULE, UML_TO_PCM_HASH),
                    Map.of()),
                new RuleAwareSpecification(
                    UmlPcmMockupSpecifications.pcmToUml(),
                    Map.of(UML_TO_PCM_RULE, PCM_TO_UML_HASH),
                    Map.of())));

    // the winning hash depends on the propagator's specification order, only one entry is
    // guaranteed:
    Map<ConsistencyRuleId, String> loaded = PersistedRules.load(folder).hashes();
    assertEquals(Set.of(UML_TO_PCM_RULE), loaded.keySet());
    assertTrue(List.of(UML_TO_PCM_HASH, PCM_TO_UML_HASH).contains(loaded.get(UML_TO_PCM_RULE)));
  }

  @SuppressWarnings("UnstableApiUsage")
  private Path seededVsum(String name, List<ChangePropagationSpecification> specifications)
      throws Exception {
    Path folder = Files.createDirectories(tempDir.resolve(name));
    InternalVirtualModel vsum = Vsums.build(folder, specifications, new DetectingUserInteraction());
    try (View view = Vsums.openEmptyView(vsum, "seed")) {
      CommittableView committable = view.withChangeDerivingTrait();
      UPackage shop = Uml_mockupFactory.eINSTANCE.createUPackage();
      shop.setName("shop");
      committable.registerRoot(
          shop, URI.createFileURI(folder.resolve("model.uml_mockup").toString()));
      committable.commitChanges();
    } finally {
      vsum.dispose();
    }

    return folder;
  }
}
