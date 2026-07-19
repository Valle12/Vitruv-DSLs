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
import org.eclipse.emf.ecore.EObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.change.atomic.EChange;
import tools.vitruv.change.composite.MetamodelDescriptor;
import tools.vitruv.change.correspondence.Correspondence;
import tools.vitruv.change.correspondence.view.EditableCorrespondenceModelView;
import tools.vitruv.change.interaction.UserInteractor;
import tools.vitruv.change.propagation.ChangePropagationObserver;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.change.propagation.ConsistencyRuleId;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.change.utils.ResourceAccess;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.interaction.DetectingUserInteraction;
import tools.vitruv.dsls.reactions.migration.testspecs.UmlPcmMockupSpecifications;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;
import uml_mockup.UPackage;
import uml_mockup.Uml_mockupFactory;

@ExtendWith(RegisterMetamodelsInStandalone.class)
class PersistedRuleHashesTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();
  private static final ConsistencyRuleId UML_TO_PCM_RULE = ConsistencyRuleId.of("mockup::umlToPcm");
  private static final ConsistencyRuleId PCM_TO_UML_RULE = ConsistencyRuleId.of("mockup::pcmToUml");
  private static final String UML_TO_PCM_HASH = "a".repeat(64);
  private static final String PCM_TO_UML_HASH = "b".repeat(64);

  @TempDir Path tempDir;

  @BeforeAll
  static void prepareStandalone() {
    Vsums.prepareStandalone(ADAPTERS);
  }

  @Test
  @DisplayName("propagation persists rule hashes that load returns")
  void testLoadReturnsHashesPersistedByPropagation() throws Exception {
    Path folder =
        seededVsum(
            "with-hashes",
            List.of(
                new HashProvidingSpecification(
                    UmlPcmMockupSpecifications.umlToPcm(),
                    Map.of(UML_TO_PCM_RULE, UML_TO_PCM_HASH)),
                new HashProvidingSpecification(
                    UmlPcmMockupSpecifications.pcmToUml(),
                    Map.of(PCM_TO_UML_RULE, PCM_TO_UML_HASH))));

    Path registryFile =
        folder.resolve("consistencymetadata").resolve("vitruv").resolve("rule-hashes.txt");
    assertTrue(
        Files.exists(registryFile),
        "the registry must be persisted in the consistency metadata folder at " + registryFile);
    assertEquals(
        Map.of(UML_TO_PCM_RULE, UML_TO_PCM_HASH, PCM_TO_UML_RULE, PCM_TO_UML_HASH),
        PersistedRuleHashes.load(folder));
  }

  @Test
  @DisplayName("load returns an empty map if no registry was persisted")
  void testLoadWithoutPersistedRegistryReturnsEmptyMap() throws Exception {
    Path folder =
        seededVsum(
            "without-hashes",
            List.of(UmlPcmMockupSpecifications.umlToPcm(), UmlPcmMockupSpecifications.pcmToUml()));

    assertFalse(
        Files.exists(folder.resolve("consistencymetadata").resolve("vitruv")),
        "specifications without rule hashes must not persist a registry");
    assertEquals(Map.of(), PersistedRuleHashes.load(folder));
  }

  @Test
  @DisplayName("duplicate rule ids collapse to a single persisted entry")
  void testLoadCollapsesDuplicateIdsToSingleEntry() throws Exception {
    Path folder =
        seededVsum(
            "duplicate-ids",
            List.of(
                new HashProvidingSpecification(
                    UmlPcmMockupSpecifications.umlToPcm(),
                    Map.of(UML_TO_PCM_RULE, UML_TO_PCM_HASH)),
                new HashProvidingSpecification(
                    UmlPcmMockupSpecifications.pcmToUml(),
                    Map.of(UML_TO_PCM_RULE, PCM_TO_UML_HASH))));

    // the winning hash depends on the propagator's specification order, only one entry is guaranteed:
    Map<ConsistencyRuleId, String> loaded = PersistedRuleHashes.load(folder);
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

  /** Delegates to a wrapped specification, but exposes a fixed rule-hash map like a generated one. */
  private static final class HashProvidingSpecification implements ChangePropagationSpecification {
    private final ChangePropagationSpecification delegate;
    private final Map<ConsistencyRuleId, String> ruleHashes;

    private HashProvidingSpecification(
        ChangePropagationSpecification delegate, Map<ConsistencyRuleId, String> ruleHashes) {
      this.delegate = delegate;
      this.ruleHashes = ruleHashes;
    }

    @Override
    public Map<ConsistencyRuleId, String> getConsistencyRuleHashes() {
      return ruleHashes;
    }

    @Override
    public void setUserInteractor(UserInteractor userInteractor) {
      delegate.setUserInteractor(userInteractor);
    }

    @Override
    public MetamodelDescriptor getSourceMetamodelDescriptor() {
      return delegate.getSourceMetamodelDescriptor();
    }

    @Override
    public MetamodelDescriptor getTargetMetamodelDescriptor() {
      return delegate.getTargetMetamodelDescriptor();
    }

    @Override
    public boolean doesHandleChange(
        EChange<EObject> change,
        EditableCorrespondenceModelView<Correspondence> correspondenceModel) {
      return delegate.doesHandleChange(change, correspondenceModel);
    }

    @Override
    public void propagateChange(
        EChange<EObject> change,
        EditableCorrespondenceModelView<Correspondence> correspondenceModel,
        ResourceAccess resourceAccess) {
      delegate.propagateChange(change, correspondenceModel, resourceAccess);
    }

    @Override
    public void notifyObjectCreated(EObject createdObject) {
      delegate.notifyObjectCreated(createdObject);
    }

    @Override
    public void notifyChangePropagationStarted(
        ChangePropagationSpecification specification, EChange<EObject> change) {
      delegate.notifyChangePropagationStarted(specification, change);
    }

    @Override
    public void notifyChangePropagationStopped(
        ChangePropagationSpecification specification, EChange<EObject> change) {
      delegate.notifyChangePropagationStopped(specification, change);
    }

    @Override
    public void registerObserver(ChangePropagationObserver observer) {
      delegate.registerObserver(observer);
    }

    @Override
    public void deregisterObserver(ChangePropagationObserver observer) {
      delegate.deregisterObserver(observer);
    }
  }
}
