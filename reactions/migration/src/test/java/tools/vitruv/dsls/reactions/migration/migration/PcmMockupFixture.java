package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.emf.common.util.URI;
import pcm_mockup.PInterface;
import pcm_mockup.PMethod;
import pcm_mockup.Pcm_mockupFactory;
import pcm_mockup.Repository;
import tools.vitruv.dsls.reactions.migration.interaction.DetectingUserInteraction;
import tools.vitruv.dsls.reactions.migration.spec.SpecificationSource;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;
import uml_mockup.UInterface;
import uml_mockup.UPackage;
import uml_mockup.Uml_mockupFactory;

final class PcmMockupFixture {
  static final String DERIVED_METHOD = "ping";
  static final String HAND_WRITTEN_METHOD = "helper";

  private PcmMockupFixture() {}

  static Repository repository(Path folder) {
    return repository(folder, "model.pcm_mockup");
  }

  static Repository repository(Path folder, String fileName) {
    return (Repository) TwoMetamodelMockupMigrationTest.loadRoot(folder.resolve(fileName));
  }

  static Set<String> methodNamesOf(Repository repository) {
    return repository.getInterfaces().stream()
        .flatMap(pInterface -> pInterface.getMethods().stream())
        .map(PMethod::getName)
        .collect(Collectors.toUnmodifiableSet());
  }

  static Set<String> methodNamesOfInterface(Path folder) {
    return methodNamesOf(repository(folder));
  }

  @SuppressWarnings("UnstableApiUsage")
  static Path seedWithHandWrittenMethod(Path folder, SpecificationSource rules) throws Exception {
    Files.createDirectories(folder);
    InternalVirtualModel vsum =
        Vsums.build(folder, rules.createSpecifications(), new DetectingUserInteraction());
    try (View view = Vsums.openEmptyView(vsum, "seed")) {
      CommittableView committable = view.withChangeDerivingTrait();
      UPackage shop = Uml_mockupFactory.eINSTANCE.createUPackage();
      shop.setName("shop");
      UInterface api = Uml_mockupFactory.eINSTANCE.createUInterface();
      api.setName("Api");
      shop.getInterfaces().add(api);
      var ping = Uml_mockupFactory.eINSTANCE.createUMethod();
      ping.setName(DERIVED_METHOD);
      api.getMethods().add(ping);
      committable.registerRoot(
          shop, URI.createFileURI(folder.resolve("model.uml_mockup").toString()));
      committable.commitChanges();
    } finally {
      vsum.dispose();
    }

    addHandWrittenMethod(folder, rules);
    assertEquals(Set.of(DERIVED_METHOD, HAND_WRITTEN_METHOD), methodNamesOfInterface(folder));
    return folder;
  }

  static void addHandWrittenMethod(Path folder, SpecificationSource rules) throws Exception {
    InternalVirtualModel vsum =
        Vsums.build(folder, rules.createSpecifications(), new DetectingUserInteraction());
    try (View view = Vsums.openViewOfAll(vsum, "hand-written-edit")) {
      CommittableView committable = view.withChangeDerivingTrait();
      PInterface api =
          view.getRootObjects().stream()
              .filter(Repository.class::isInstance)
              .map(Repository.class::cast)
              .flatMap(repository -> repository.getInterfaces().stream())
              .findFirst()
              .orElseThrow();
      PMethod handWritten = Pcm_mockupFactory.eINSTANCE.createPMethod();
      handWritten.setName(HAND_WRITTEN_METHOD);
      api.getMethods().add(handWritten);
      committable.commitChanges();
    } finally {
      vsum.dispose();
    }
  }

  static void linkHandWrittenMethodToHandWrittenInterface(Path folder, SpecificationSource rules)
      throws Exception {
    InternalVirtualModel vsum =
        Vsums.build(folder, rules.createSpecifications(), new DetectingUserInteraction());
    try (View view = Vsums.openViewOfAll(vsum, "hand-written-link")) {
      CommittableView committable = view.withChangeDerivingTrait();
      Repository repository =
          view.getRootObjects().stream()
              .filter(Repository.class::isInstance)
              .map(Repository.class::cast)
              .findFirst()
              .orElseThrow();
      PInterface notes = Pcm_mockupFactory.eINSTANCE.createPInterface();
      notes.setName("Notes");
      repository.getInterfaces().add(notes);
      PMethod helper =
          repository.getInterfaces().stream()
              .flatMap(pInterface -> pInterface.getMethods().stream())
              .filter(method -> HAND_WRITTEN_METHOD.equals(method.getName()))
              .findFirst()
              .orElseThrow();
      helper.setReturnType(notes);
      committable.commitChanges();
    } finally {
      vsum.dispose();
    }
  }
}
