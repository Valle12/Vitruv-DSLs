package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.BACK_COMPONENT_INSERTED;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.BACK_HASH;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.CLASS_HASH_V2;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.CLASS_INSERTED;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.CLASS_TRIGGER;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.INTERFACE_HASH;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.INTERFACE_INSERTED;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.METHOD_HASH;
import static tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupSpecifications.METHOD_INSERTED;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.emf.common.util.URI;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import pcm_mockup.Component;
import pcm_mockup.Repository;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;
import tools.vitruv.dsls.reactions.migration.interaction.DetectingUserInteraction;
import tools.vitruv.dsls.reactions.migration.preservation.PreservationPolicy;
import tools.vitruv.dsls.reactions.migration.spec.SpecificationSource;
import tools.vitruv.dsls.reactions.migration.strategy.ExplicitDominance;
import tools.vitruv.dsls.reactions.migration.testspecs.SelectiveMockupWorlds;
import tools.vitruv.dsls.reactions.migration.vsum.PersistedRules;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;
import uml_mockup.UClass;
import uml_mockup.UInterface;
import uml_mockup.UPackage;
import uml_mockup.Uml_mockupFactory;

@ExtendWith(RegisterMetamodelsInStandalone.class)
class SelectiveMigrationTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();

  @TempDir Path tempDir;

  @BeforeAll
  static void prepareStandalone() {
    Vsums.prepareStandalone(ADAPTERS);
  }

  private static MigrationReport migrate(
      Path folder, SpecificationSource newSpecifications, MigrationMode mode) {
    return new MigrationRunner(
            newSpecifications,
            new PropagationGraph(newSpecifications.createSpecifications()),
            new ExplicitDominance("uml_mockup"),
            null,
            ADAPTERS,
            new MigrationSettings(false, 0, mode, PreservationPolicy.OFF, false))
        .run(folder);
  }

  private static UPackage umlPackage(Path folder) {
    return (UPackage) TwoMetamodelMockupMigrationTest.loadRoot(folder.resolve("model.uml_mockup"));
  }

  private static Repository pcmRepository(Path folder) {
    return (Repository)
        TwoMetamodelMockupMigrationTest.loadRoot(folder.resolve("model.pcm_mockup"));
  }

  private static Set<String> pcmComponentNames(Path folder) {
    return pcmRepository(folder).getComponents().stream()
        .map(Component::getName)
        .collect(Collectors.toUnmodifiableSet());
  }

  @Test
  @DisplayName("a renamed rule repropagates only its trigger elements")
  void test1() throws Exception {
    Path folder = seededVsum();

    MigrationReport report =
        migrate(folder, SelectiveMockupWorlds.withRenamedClassRule(), MigrationMode.ID_DIFF);

    assertTrue(report.migrated());
    assertTrue(report.selective().attempted());
    assertFalse(report.selective().fellBackToFull());
    assertEquals(2, report.selective().dirtyRuleCount());
    assertEquals(1, report.selective().affectedElementCount());
    assertEquals(Set.of("Counter"), pcmComponentNames(folder));
    assertEquals(1, pcmRepository(folder).getInterfaces().size());
    assertEquals(1, pcmRepository(folder).getInterfaces().getFirst().getMethods().size());
    assertEquals(1, umlPackage(folder).getClasses().size());

    MigrationReport secondRun =
        migrate(folder, SelectiveMockupWorlds.withRenamedClassRule(), MigrationMode.ID_DIFF);
    assertFalse(secondRun.migrated());
    assertEquals(0, secondRun.selective().dirtyRuleCount());
  }

  @Test
  @DisplayName("an edited rule is invisible to id mode")
  void test2() throws Exception {
    Path folder = seededVsum();

    MigrationReport report =
        migrate(folder, SelectiveMockupWorlds.withEditedClassRule(), MigrationMode.ID_DIFF);

    assertFalse(report.migrated());
    assertTrue(report.selective().attempted());
    assertEquals(0, report.selective().dirtyRuleCount());
    assertEquals(Set.of("Counter"), pcmComponentNames(folder));
    assertNull(
        pcmRepository(folder).getComponents().getFirst().getComponentExclusive(),
        "id mode must not apply the edited rule");
  }

  @Test
  @DisplayName("an edited rule is repropagated by hash mode")
  void test3() throws Exception {
    Path folder = seededVsum();

    MigrationReport report =
        migrate(folder, SelectiveMockupWorlds.withEditedClassRule(), MigrationMode.HASH_DIFF);

    assertTrue(report.migrated());
    assertEquals(1, report.selective().dirtyRuleCount());
    assertEquals(1, report.selective().affectedElementCount());
    assertEquals(Set.of("Counter"), pcmComponentNames(folder));
    assertEquals(
        "v2",
        pcmRepository(folder).getComponents().getFirst().getComponentExclusive(),
        "hash mode must re-derive the component with the edited rule");
    assertEquals(1, pcmRepository(folder).getInterfaces().size());
    assertEquals(1, pcmRepository(folder).getInterfaces().getFirst().getMethods().size());
    assertEquals(1, umlPackage(folder).getClasses().size());
  }

  @Test
  @DisplayName("a removed rule's elements lose their derived artifacts and keep their sources")
  void test4() throws Exception {
    Path folder = seededVsum();

    MigrationReport report =
        migrate(folder, SelectiveMockupWorlds.withoutMethodRule(), MigrationMode.ID_DIFF);

    assertTrue(report.migrated());
    assertEquals(1, report.selective().dirtyRuleCount());
    assertEquals(1, report.selective().affectedElementCount());
    assertEquals(1, umlPackage(folder).getInterfaces().getFirst().getMethods().size());
    assertEquals(0, pcmRepository(folder).getInterfaces().getFirst().getMethods().size());
    assertEquals(Set.of("Counter"), pcmComponentNames(folder));

    SelectiveOutcome.RuleDiff rules = report.selective().rules();
    assertEquals(
        List.of(
            new SelectiveOutcome.DirtyRule(
                METHOD_INSERTED.value(), SelectiveOutcome.DirtyRule.Kind.REMOVED, 1)),
        rules.dirty(),
        "the removed rule is the dirty one, and its trigger matched the one method");
    assertEquals(rules.current() + 1, rules.persisted());
    SelectiveOutcome.AffectedElement affected = report.selective().affectedElements().getFirst();
    assertTrue(affected.key().contains("model.uml_mockup#"), affected.key());
    assertEquals("uml_mockup::UMethod", affected.metaclass());
    assertEquals(List.of(METHOD_INSERTED.value()), affected.matchedBy());
    assertTrue(affected.refersTo().isEmpty());
    assertTrue(report.selective().sourceElements() > 0);
    assertTrue(report.selective().modelElements() > report.selective().sourceElements());
  }

  @Test
  @DisplayName("reinserting an affected container reconstructs its children")
  void test5() throws Exception {
    Path folder = seededVsum();

    MigrationReport report =
        migrate(folder, SelectiveMockupWorlds.withRenamedInterfaceRule(), MigrationMode.ID_DIFF);

    assertTrue(report.migrated());
    assertEquals(2, report.selective().dirtyRuleCount());
    assertEquals(1, report.selective().affectedElementCount());
    assertEquals(1, pcmRepository(folder).getInterfaces().size());
    assertEquals(
        1,
        pcmRepository(folder).getInterfaces().getFirst().getMethods().size(),
        "the reinserted interface subtree must re-derive its method mirror");
    assertEquals(Set.of("Counter"), pcmComponentNames(folder));
    assertEquals(1, umlPackage(folder).getInterfaces().getFirst().getMethods().size());
  }

  @Test
  @DisplayName("a repropagating run reports the timing of each selective phase")
  void test7() throws Exception {
    Path folder = seededVsum();

    MigrationReport report =
        migrate(folder, SelectiveMockupWorlds.withRenamedClassRule(), MigrationMode.ID_DIFF);

    assertEquals(
        List.of(
            "rule-diff",
            "load",
            "roots",
            "dominance",
            "classify",
            "left-out",
            "view-open",
            "selection",
            "preregister",
            "delete-commit",
            "reinsert-commit",
            "view-close",
            "refresh"),
        report.statistics().phaseNames());
    assertFalse(report.statistics().total().isNegative());
  }

  @Test
  @DisplayName("selective commits refresh the persisted registry with the new rules")
  void test6() throws Exception {
    Path folder = seededVsum();

    migrate(folder, SelectiveMockupWorlds.withEditedClassRule(), MigrationMode.HASH_DIFF);

    PersistedRules persisted = PersistedRules.load(folder);
    assertEquals(
        Map.of(
            CLASS_INSERTED, CLASS_HASH_V2,
            INTERFACE_INSERTED, INTERFACE_HASH,
            METHOD_INSERTED, METHOD_HASH,
            BACK_COMPONENT_INSERTED, BACK_HASH),
        persisted.hashes());
    assertEquals(CLASS_TRIGGER, persisted.triggers().get(CLASS_INSERTED));
  }

  @Test
  @DisplayName("several affected siblings return in the order they were detached in")
  void test8() throws Exception {
    Path folder = seededVsum("Alpha", "Beta", "Gamma");

    MigrationReport report =
        migrate(folder, SelectiveMockupWorlds.withRenamedClassRule(), MigrationMode.ID_DIFF);

    assertEquals(3, report.selective().affectedElementCount());
    assertEquals(
        List.of("Alpha", "Beta", "Gamma"),
        umlPackage(folder).getClasses().stream().map(UClass::getName).toList(),
        "detaching and reinserting several siblings must not reorder them");
    assertEquals(
        List.of("Alpha", "Beta", "Gamma"),
        pcmRepository(folder).getComponents().stream().map(Component::getName).toList(),
        "so their re-derived counterparts keep that order as well");
  }

  private Path seededVsum() throws Exception {
    return seededVsum("Counter");
  }

  @SuppressWarnings("UnstableApiUsage")
  private Path seededVsum(String... classNames) throws Exception {
    Path folder = Files.createDirectories(tempDir.resolve("vsum"));
    InternalVirtualModel vsum =
        Vsums.build(
            folder,
            SelectiveMockupWorlds.version1().createSpecifications(),
            new DetectingUserInteraction());
    try (View view = Vsums.openEmptyView(vsum, "seed")) {
      CommittableView committable = view.withChangeDerivingTrait();
      UPackage shop = Uml_mockupFactory.eINSTANCE.createUPackage();
      shop.setName("shop");
      for (String className : classNames) {
        UClass uClass = Uml_mockupFactory.eINSTANCE.createUClass();
        uClass.setName(className);
        shop.getClasses().add(uClass);
      }
      UInterface api = Uml_mockupFactory.eINSTANCE.createUInterface();
      shop.getInterfaces().add(api);
      api.getMethods().add(Uml_mockupFactory.eINSTANCE.createUMethod());
      committable.registerRoot(
          shop, URI.createFileURI(folder.resolve("model.uml_mockup").toString()));
      committable.commitChanges();
    } finally {
      vsum.dispose();
    }

    assertEquals(Set.of(classNames), pcmComponentNames(folder));
    assertEquals(1, pcmRepository(folder).getInterfaces().size());
    assertEquals(1, pcmRepository(folder).getInterfaces().getFirst().getMethods().size());
    assertFalse(PersistedRules.load(folder).hashes().isEmpty());
    return folder;
  }
}
