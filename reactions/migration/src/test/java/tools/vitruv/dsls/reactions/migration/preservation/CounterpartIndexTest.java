package tools.vitruv.dsls.reactions.migration.preservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.emf.ecore.EObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import pcm_mockup.PInterface;
import pcm_mockup.Pcm_mockupFactory;
import pcm_mockup.Repository;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;

@ExtendWith(RegisterMetamodelsInStandalone.class)
class CounterpartIndexTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();

  @TempDir Path tempDir;

  private CounterpartIndex index;

  private static Repository repository(String name, String... interfaceNames) {
    Repository repository = Pcm_mockupFactory.eINSTANCE.createRepository();
    repository.setName(name);
    for (String interfaceName : interfaceNames) {
      PInterface pInterface = Pcm_mockupFactory.eINSTANCE.createPInterface();
      pInterface.setName(interfaceName);
      repository.getInterfaces().add(pInterface);
    }

    return repository;
  }

  @BeforeEach
  void emptyIndex() throws Exception {
    VsumState empty = VsumState.load(Files.createDirectories(tempDir.resolve("empty")), ADAPTERS);
    index =
        CounterpartIndex.build(
            empty, empty, root -> false, AmbiguityResolver.reporting(tempDir, tempDir));
  }

  @Test
  @DisplayName("the file most counterparts landed in continues the old one")
  void test1() {
    Repository old = repository("shop", "Api", "Billing", "Notes");
    Repository moved = repository("shop", "Api", "Billing");
    Repository elsewhere = repository("other", "Notes");
    index.addCounterpart(old.getInterfaces().get(0), moved.getInterfaces().get(0));
    index.addCounterpart(old.getInterfaces().get(1), moved.getInterfaces().get(1));
    index.addCounterpart(old.getInterfaces().get(2), elsewhere.getInterfaces().getFirst());

    assertEquals(List.of(moved), index.counterpartRootsOf(old));
  }

  @Test
  @DisplayName("an evenly split file leaves the choice open")
  void test2() {
    Repository old = repository("shop", "Api", "Billing");
    Repository first = repository("shop", "Api");
    Repository second = repository("shop", "Billing");
    index.addCounterpart(old.getInterfaces().get(0), first.getInterfaces().getFirst());
    index.addCounterpart(old.getInterfaces().get(1), second.getInterfaces().getFirst());

    List<EObject> roots = index.counterpartRootsOf(old);

    assertEquals(2, roots.size(), "nobody can tell which file continues the old one: " + roots);
    assertTrue(roots.containsAll(List.of(first, second)));
  }

  @Test
  @DisplayName("without any counterpart there is nothing pointing at a file")
  void test3() {
    assertEquals(List.of(), index.counterpartRootsOf(repository("shop", "Api")));
  }
}
