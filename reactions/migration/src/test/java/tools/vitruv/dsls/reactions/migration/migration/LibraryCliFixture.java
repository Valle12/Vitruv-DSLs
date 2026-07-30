package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.dsls.reactions.migration.Main;
import tools.vitruv.dsls.reactions.migration.interaction.DetectingUserInteraction;
import tools.vitruv.dsls.reactions.migration.spec.JarSpecificationSource;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

final class LibraryCliFixture {
  private LibraryCliFixture() {}

  static TestUserInteraction permissiveInteraction() {
    return permissiveInteraction("library");
  }

  static TestUserInteraction permissiveInteraction(String textAnswer) {
    TestUserInteraction interaction = new TestUserInteraction();
    interaction.onTextInput(description -> true).always().respondWith(textAnswer);
    interaction
        .onMultipleChoiceSingleSelection(description -> true)
        .always()
        .respondWithChoiceAt(0);
    interaction.onConfirmation(description -> true).always().respondWith(true);
    interaction.acknowledgeNotification(description -> true);
    return interaction;
  }

  static int runCli(Path vsumFolder, Path propagationsJar, String... extraArguments) {
    List<String> arguments =
        new ArrayList<>(
            List.of(
                "--model",
                vsumFolder.toString(),
                "--propagations",
                propagationsJar.toString(),
                "--dominant",
                "uml"));
    arguments.addAll(List.of(extraArguments));
    return Main.run(
        arguments.toArray(String[]::new),
        new TestUserInteraction.ResultProvider(permissiveInteraction()));
  }

  static void writeMethodBody(Path vsumFolder) throws Exception {
    Path book = vsumFolder.resolve("src").resolve("catalog").resolve("Book.java");
    String source = Files.readString(book);
    String signature = "void describe() {";
    assertTrue(
        source.contains(signature), "the fixture must derive an empty describe(): " + source);
    Files.writeString(
        book, source.replace(signature, signature + "%n\t\treturn null;".formatted()));
  }

  static void assertVsumReloadsWithJarSpecs(Path vsumFolder, Path propagationsJar)
      throws Exception {
    try (JarSpecificationSource specifications = JarSpecificationSource.fromJar(propagationsJar)) {
      InternalVirtualModel reloaded =
          Vsums.build(
              vsumFolder, specifications.createSpecifications(), new DetectingUserInteraction());
      try {
        assertFalse(Vsums.readRoots(reloaded).isEmpty(), "the migrated VSUM must reload");
      } finally {
        reloaded.dispose();
      }
    }
  }
}
