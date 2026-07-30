package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import pcm_mockup.PInterface;
import pcm_mockup.PNamedElement;
import pcm_mockup.Repository;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.interaction.DetectingUserInteraction;
import tools.vitruv.dsls.reactions.migration.testspecs.UmlPcmMockupSpecifications;
import tools.vitruv.dsls.reactions.migration.vsum.ModelSnapshot;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;
import uml_mockup.UClass;
import uml_mockup.UInterface;
import uml_mockup.UMethod;
import uml_mockup.UPackage;
import uml_mockup.Uml_mockupFactory;

@ExtendWith(RegisterMetamodelsInStandalone.class)
class ReplayerTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();

  @TempDir Path tempDir;

  @BeforeAll
  static void prepareStandalone() {
    Vsums.prepareStandalone(ADAPTERS);
  }

  private static List<ChangePropagationSpecification> mockupSpecs() {
    return List.of(UmlPcmMockupSpecifications.umlToPcm(), UmlPcmMockupSpecifications.pcmToUml());
  }

  private static UPackage shopPackage() {
    UPackage shop = Uml_mockupFactory.eINSTANCE.createUPackage();
    shop.setName("shop");
    UClass order = Uml_mockupFactory.eINSTANCE.createUClass();
    order.setName("Order");
    shop.getClasses().add(order);
    UInterface api = Uml_mockupFactory.eINSTANCE.createUInterface();
    api.setName("Api");
    UMethod get = Uml_mockupFactory.eINSTANCE.createUMethod();
    get.setName("get");
    api.getMethods().add(get);
    shop.getInterfaces().add(api);
    return shop;
  }

  private static void assertRepositoryMirrorsShop(Path pcmFile) {
    assertTrue(Files.exists(pcmFile), "pcm mirror must be persisted at " + pcmFile);
    Repository repository =
        (Repository)
            new ResourceSetImpl()
                .getResource(URI.createFileURI(pcmFile.toString()), true)
                .getContents()
                .getFirst();
    assertEquals("shop", repository.getName());
    assertEquals(
        List.of("Order"), repository.getComponents().stream().map(PNamedElement::getName).toList());
    assertEquals(
        List.of("Api"), repository.getInterfaces().stream().map(PInterface::getName).toList());
    assertEquals(
        List.of("get"),
        repository.getInterfaces().getFirst().getMethods().stream()
            .map(PNamedElement::getName)
            .toList());
  }

  @Test
  @DisplayName("seeded UML package propagates to PCM and replays into a fresh VSUM")
  void test1() throws Exception {
    Path originalFolder = Files.createDirectories(tempDir.resolve("original"));
    URI umlUri = URI.createFileURI(originalFolder.resolve("model.uml_mockup").toString());
    seedShopModel(originalFolder, umlUri);

    assertRepositoryMirrorsShop(originalFolder.resolve("model.pcm_mockup"));

    Path replayFolder = Files.createDirectories(tempDir.resolve("replay"));
    ModelSnapshot snapshot =
        ModelSnapshot.of(List.of(umlUri), ADAPTERS, originalFolder)
            .relocatedTo(replayFolder, originalFolder);
    InternalVirtualModel freshVsum =
        Vsums.build(replayFolder, mockupSpecs(), new DetectingUserInteraction());
    try {
      new Replayer(ADAPTERS).replayInto(freshVsum, snapshot);
    } finally {
      freshVsum.dispose();
    }

    assertTrue(Files.exists(replayFolder.resolve("model.uml_mockup")));
    assertRepositoryMirrorsShop(replayFolder.resolve("model.pcm_mockup"));
  }

  @SuppressWarnings("UnstableApiUsage")
  private void seedShopModel(Path folder, URI umlUri) throws Exception {
    InternalVirtualModel vsum = Vsums.build(folder, mockupSpecs(), new DetectingUserInteraction());
    try (View view = Vsums.openEmptyView(vsum, "seed")) {
      CommittableView committable = view.withChangeDerivingTrait();
      committable.registerRoot(shopPackage(), umlUri);
      committable.commitChanges();
    } finally {
      vsum.dispose();
    }
  }
}
