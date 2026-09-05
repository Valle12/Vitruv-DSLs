package tools.vitruv.dsls.reactions.migration.preservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import allElementTypes.AllElementTypesFactory;
import allElementTypes.Root;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;

@ExtendWith(RegisterMetamodelsInStandalone.class)
class CandidateCollectorTest {
  private static final String MODEL_FILE = "model.allelementtypes";
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();

  @TempDir Path tempDir;

  @BeforeAll
  static void prepareStandalone() {
    Vsums.prepareStandalone(ADAPTERS);
  }

  private static Path folderWithRoot(Path folder, List<Integer> multiValued) throws IOException {
    Files.createDirectories(folder);
    Root root = AllElementTypesFactory.eINSTANCE.createRoot();
    root.setId("theRoot");
    root.getMultiValuedEAttribute().addAll(multiValued);

    ResourceSet resourceSet = ADAPTERS.newResourceSet();
    Resource resource =
        resourceSet.createResource(
            URI.createFileURI(folder.resolve(MODEL_FILE).toAbsolutePath().toString()));
    resource.getContents().add(root);
    resource.save(null);
    return folder;
  }

  private static List<PreservationCandidate> multiValuedCandidates(CollectedContent content) {
    return content.candidates().stream()
        .filter(candidate -> "multiValuedEAttribute".equals(candidate.oldFeature().getName()))
        .toList();
  }

  private CollectedContent collect(List<Integer> oldValues, List<Integer> newValues)
      throws IOException {
    Path oldFolder = folderWithRoot(tempDir.resolve("old"), oldValues);
    Path newFolder = folderWithRoot(tempDir.resolve("new"), newValues);
    VsumState oldState = VsumState.load(oldFolder, ADAPTERS);
    VsumState newState = VsumState.load(newFolder, ADAPTERS);
    AmbiguityResolver resolver = AmbiguityResolver.reporting(oldFolder, newFolder);
    CounterpartIndex counterparts =
        CounterpartIndex.build(oldState, newState, root -> true, resolver);
    return new CandidateCollector(
            oldState,
            newState,
            counterparts,
            PreservationPolicy.USER,
            root -> true,
            resolver,
            new ExternalTargets(ADAPTERS, oldFolder, newFolder))
        .collect();
  }

  @Test
  @DisplayName("the counterpart keeps its own many-valued attribute instead of being merged into")
  void test1() throws Exception {
    CollectedContent content = collect(List.of(7), List.of(1, 2));

    assertEquals(
        List.of(),
        multiValuedCandidates(content),
        "appending the old entry would make the migrated value [1, 2, 7], which nobody wrote");
    assertTrue(
        content.decisions().stream()
            .anyMatch(
                decision ->
                    decision.detail() != null
                        && decision.detail().contains("multiValuedEAttribute is now [1, 2]")),
        "and the difference is reported rather than merged away: " + content.decisions());
  }

  @Test
  @DisplayName("an empty counterpart value still gets the old one carried over whole")
  void test2() throws Exception {
    CollectedContent content = collect(List.of(7, 8), List.of());

    assertEquals(
        List.of(7, 8),
        multiValuedCandidates(content).stream().map(PreservationCandidate::oldValue).toList(),
        "nothing of the counterpart's own can be corrupted here, so the old value is preserved");
  }

  @Test
  @DisplayName("an unchanged many-valued attribute produces neither a candidate nor a decision")
  void test3() throws Exception {
    CollectedContent content = collect(List.of(1, 2), List.of(1, 2));

    assertEquals(List.of(), multiValuedCandidates(content));
    assertEquals(List.of(), content.decisions(), "there is nothing to tell the user about");
  }

  @Test
  @DisplayName("a many-valued attribute the new rules only reordered is not a changed value")
  void test4() throws Exception {
    CollectedContent content = collect(List.of(1, 2), List.of(2, 1));

    assertEquals(List.of(), multiValuedCandidates(content));
    assertEquals(
        List.of(),
        content.decisions(),
        "the list still holds everything it held, so nothing was replaced");
  }

  @Test
  @DisplayName("a many-valued attribute that lost a value is still reported")
  void test5() throws Exception {
    CollectedContent content = collect(List.of(1, 2), List.of(2, 2));

    assertEquals(
        1,
        content.decisions().size(),
        "the same values in another order is not the same as a value replaced by a duplicate: "
            + content.decisions());
  }
}
