package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.BACK_COMPONENT_INSERTED;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.BACK_COMPONENT_TRIGGER;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.BACK_HASH;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.BACK_INTERFACE_INSERTED;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.BACK_INTERFACE_INSERTED_V2;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.BACK_INTERFACE_TRIGGER;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.INTERFACE_HASH;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.INTERFACE_HASH_V2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.eclipse.emf.common.util.URI;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import pcm_mockup.Component;
import pcm_mockup.PInterface;
import pcm_mockup.Pcm_mockupFactory;
import pcm_mockup.Repository;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;
import tools.vitruv.dsls.reactions.migration.interaction.DetectingUserInteraction;
import tools.vitruv.dsls.reactions.migration.preservation.PreservationPolicy;
import tools.vitruv.dsls.reactions.migration.spec.SpecificationSource;
import tools.vitruv.dsls.reactions.migration.strategy.ExplicitDominance;
import tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications;
import tools.vitruv.dsls.reactions.migration.testspecs.UmlPcmMockupSpecifications;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;
import uml_mockup.UPackage;

@ExtendWith(RegisterMetamodelsInStandalone.class)
class SelectiveIncomingReferenceTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();

  @TempDir Path tempDir;

  @BeforeAll
  static void prepareStandalone() {
    Vsums.prepareStandalone(ADAPTERS);
  }

  private static SpecificationSource oldWorld() {
    return () ->
        List.of(
            SelectiveMockupSpecifications.pcmToUml(
                Map.of(BACK_INTERFACE_INSERTED, INTERFACE_HASH, BACK_COMPONENT_INSERTED, BACK_HASH),
                Map.of(
                    BACK_INTERFACE_INSERTED,
                    BACK_INTERFACE_TRIGGER,
                    BACK_COMPONENT_INSERTED,
                    BACK_COMPONENT_TRIGGER)),
            UmlPcmMockupSpecifications.umlToPcm());
  }

  private static SpecificationSource worldWithRenamedInterfaceRule() {
    return () ->
        List.of(
            SelectiveMockupSpecifications.pcmToUml(
                Map.of(
                    BACK_INTERFACE_INSERTED_V2,
                    INTERFACE_HASH_V2,
                    BACK_COMPONENT_INSERTED,
                    BACK_HASH),
                Map.of(
                    BACK_INTERFACE_INSERTED_V2,
                    BACK_INTERFACE_TRIGGER,
                    BACK_COMPONENT_INSERTED,
                    BACK_COMPONENT_TRIGGER)),
            UmlPcmMockupSpecifications.umlToPcm());
  }

  private static Repository pcmRepository(Path folder) {
    return (Repository)
        TwoMetamodelMockupMigrationTest.loadRoot(folder.resolve("model.pcm_mockup"));
  }

  @Test
  @DisplayName("a cross-reference into a repropagated element survives, and its owner is rebuilt")
  void test1() throws Exception {
    Path folder = seededVsum();

    MigrationReport report =
        new MigrationRunner(
                worldWithRenamedInterfaceRule(),
                new PropagationGraph(worldWithRenamedInterfaceRule().createSpecifications()),
                new ExplicitDominance("pcm_mockup"),
                null,
                ADAPTERS,
                new MigrationSettings(
                    false, 0, MigrationMode.ID_DIFF, PreservationPolicy.OFF, false))
            .run(folder);

    assertTrue(report.migrated());
    assertEquals(
        2,
        report.selective().affectedElementCount(),
        "the interface the rule names, and the component pointing at it");
    Repository repository = pcmRepository(folder);
    assertEquals(1, repository.getInterfaces().size());
    assertEquals(1, repository.getComponents().size());
    Component component = repository.getComponents().getFirst();
    assertNotNull(component.getProvidedInterface(), "the cross-reference must be restored");
    assertSame(repository.getInterfaces().getFirst(), component.getProvidedInterface());
    UPackage umlMirror =
        (UPackage) TwoMetamodelMockupMigrationTest.loadRoot(folder.resolve("model.uml_mockup"));
    assertEquals(1, umlMirror.getInterfaces().size());
    assertEquals(1, umlMirror.getClasses().size());
  }

  @SuppressWarnings("UnstableApiUsage")
  private Path seededVsum() throws Exception {
    Path folder = Files.createDirectories(tempDir.resolve("vsum"));
    InternalVirtualModel vsum =
        Vsums.build(folder, oldWorld().createSpecifications(), new DetectingUserInteraction());
    try (View view = Vsums.openEmptyView(vsum, "seed")) {
      CommittableView committable = view.withChangeDerivingTrait();
      Repository repository = Pcm_mockupFactory.eINSTANCE.createRepository();
      repository.setName("repo");
      PInterface api = Pcm_mockupFactory.eINSTANCE.createPInterface();
      repository.getInterfaces().add(api);
      Component app = Pcm_mockupFactory.eINSTANCE.createComponent();
      app.setName("App");
      app.setProvidedInterface(api);
      repository.getComponents().add(app);
      committable.registerRoot(
          repository, URI.createFileURI(folder.resolve("model.pcm_mockup").toString()));
      committable.commitChanges();
    } finally {
      vsum.dispose();
    }

    Repository seeded = pcmRepository(folder);
    assertEquals(1, seeded.getInterfaces().size());
    assertNotNull(seeded.getComponents().getFirst().getProvidedInterface());
    return folder;
  }
}
