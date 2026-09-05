package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.kit.ipd.sdq.metamodels.cad.CAD_Model;
import edu.kit.ipd.sdq.metamodels.cad.NumericParameter;
import edu.kit.ipd.sdq.metamodels.cad.Parameter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import lombok.SneakyThrows;
import mir.reactions.autosar2simulink.Autosar2simulinkChangePropagationSpecification;
import mir.reactions.brakesystem2cad.Brakesystem2cadChangePropagationSpecification;
import mir.reactions.cad2brakesystem.Cad2brakesystemChangePropagationSpecification;
import mir.reactions.cad2simulink.Cad2simulinkChangePropagationSpecification;
import mir.reactions.simulink2autosar.Simulink2autosarChangePropagationSpecification;
import mir.reactions.simulink2cad.Simulink2cadChangePropagationSpecification;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import simulink.Block;
import simulink.InPort;
import simulink.SimuLinkFactory;
import simulink.SimulinkModel;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.dsls.reactions.migration.TestDominanceContexts;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.graph.DominancePlan;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;
import tools.vitruv.dsls.reactions.migration.graph.MigrationPlanner;
import tools.vitruv.dsls.reactions.migration.graph.PropagationGraph;
import tools.vitruv.dsls.reactions.migration.preservation.PreservationPolicy;
import tools.vitruv.dsls.reactions.migration.spec.SpecificationSource;
import tools.vitruv.dsls.reactions.migration.strategy.ExplicitDominance;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

@ExtendWith(RegisterMetamodelsInStandalone.class)
class BrakeSystemMigrationTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();
  private static final String BRAKESYSTEM_NS = BrakeSystemFixture.BRAKESYSTEM_NS;
  private static final String CAD_NS = BrakeSystemFixture.CAD_NS;
  private static final String SIMULINK_NS = BrakeSystemFixture.SIMULINK_NS;
  private static final String AUTOSAR_NS = BrakeSystemFixture.AUTOSAR_NS;
  private static final Set<String> ALL_NS = BrakeSystemFixture.ALL_NS;
  private static final int DIAMETER_IN_MM = BrakeSystemFixture.DIAMETER_IN_MM;

  private static final List<ChangePropagationSpecification> BRAKE_CHAIN_SPECS =
      List.of(
          new Brakesystem2cadChangePropagationSpecification(),
          new Cad2brakesystemChangePropagationSpecification(),
          new Cad2simulinkChangePropagationSpecification(),
          new Simulink2cadChangePropagationSpecification(),
          new Simulink2autosarChangePropagationSpecification(),
          new Autosar2simulinkChangePropagationSpecification());
  private static final SpecificationSource BRAKE_CHAIN = () -> BRAKE_CHAIN_SPECS;

  @TempDir Path tempDir;

  @BeforeAll
  static void prepareStandalone() {
    Vsums.prepareStandalone(ADAPTERS);
  }

  private static MigrationReport migrate(Path folder, MigrationSettings settings) {
    return new MigrationRunner(
            BRAKE_CHAIN,
            new PropagationGraph(BRAKE_CHAIN.createSpecifications()),
            new ExplicitDominance("brakesystem"),
            BrakeSystemFixture.answering(),
            ADAPTERS,
            settings)
        .run(folder);
  }

  private static Set<String> nsUrisOnDisk(Path folder) throws IOException {
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet.getLoadOptions().putAll(ADAPTERS.combinedLoadOptions());
    Set<String> nsUris = new TreeSet<>();
    for (Path file : modelFilesIn(folder)) {
      Resource resource =
          resourceSet.getResource(URI.createFileURI(file.toAbsolutePath().toString()), true);
      assertTrue(resource.getErrors().isEmpty(), "no load errors in " + file);
      resource.getContents().forEach(root -> nsUris.add(root.eClass().getEPackage().getNsURI()));
    }

    return nsUris;
  }

  private static List<Path> modelFilesIn(Path folder) throws IOException {
    try (var walk = Files.walk(folder)) {
      return walk.filter(Files::isRegularFile)
          .filter(path -> !path.getParent().endsWith("vsum"))
          .filter(BrakeSystemMigrationTest::isModelFile)
          .toList();
    }
  }

  private static boolean isModelFile(Path path) {
    String name = path.getFileName().toString();
    return name.endsWith(".brakesystem")
        || name.endsWith(".cad")
        || name.endsWith(".simulink")
        || name.endsWith(".xml");
  }

  private static <T> T loadRootOfType(Path file, Class<T> type) {
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet.getLoadOptions().putAll(ADAPTERS.combinedLoadOptions());
    Resource resource =
        resourceSet.getResource(URI.createFileURI(file.toAbsolutePath().toString()), true);
    assertTrue(resource.getErrors().isEmpty(), "no load errors in " + file);
    EObject root = resource.getContents().getFirst();
    assertTrue(type.isInstance(root), file + " must hold a " + type.getSimpleName());
    return type.cast(root);
  }

  private static int diameterParameterIn(Path cadFile) {
    CAD_Model model = loadRootOfType(cadFile, CAD_Model.class);
    for (Parameter parameter : model.getNamespaces().getFirst().getParameters()) {
      if (parameter instanceof NumericParameter numeric && "Diameter".equals(numeric.getName())) {
        return (int) numeric.getValue();
      }
    }

    throw new AssertionError("the cad model has no Diameter parameter: " + cadFile);
  }

  private static int portCountIn(Path simulinkFile) {
    SimulinkModel model = loadRootOfType(simulinkFile, SimulinkModel.class);
    return model.getContains().stream().mapToInt(block -> block.getPorts().size()).sum();
  }

  private static Block firstBlockIn(Iterable<? extends EObject> roots) {
    for (EObject root : roots) {
      if (root instanceof SimulinkModel model && !model.getContains().isEmpty()) {
        return model.getContains().getFirst();
      }
    }

    throw new AssertionError("the reactions derived no simulink block to write into");
  }

  @Test
  @DisplayName("seeding the brake system propagates across all four metamodels")
  @SneakyThrows
  void test1() {
    Path folder = seededVsum();

    assertTrue(Files.exists(folder.resolve("example.cad")));
    assertTrue(Files.exists(folder.resolve("example.simulink")));
    assertTrue(Files.exists(folder.resolve("autosar.xml")));
    assertEquals(ALL_NS, nsUrisOnDisk(folder));
    assertEquals(DIAMETER_IN_MM, diameterParameterIn(folder.resolve("example.cad")));
  }

  @Test
  @DisplayName("the brake system alone re-derives cad, simulink and autosar")
  @SneakyThrows
  void test2() {
    Path folder = seededVsum();

    MigrationReport report = migrate(folder, MigrationSettings.forwardOnly());

    assertTrue(report.migrated());
    assertEquals(1, report.sources().size(), "the brake system covers the whole chain");
    assertEquals(3, report.derived().size(), "cad, simulink and autosar are re-derived");
    assertEquals(ALL_NS, nsUrisOnDisk(folder), "no metamodel is lost");
    assertEquals(
        DIAMETER_IN_MM,
        diameterParameterIn(folder.resolve("example.cad")),
        "the re-derived cad model carries the disk's geometry again");
    assertTrue(
        report.preservation().lost().isEmpty(),
        "unchanged rules lose nothing: " + report.preservation().lost());
    assertFalse(
        Files.exists(folder.resolve("simulink.xml")),
        "the autosar rules persist a simulink model under their own name; from the brake system"
            + " only the cad rules run, so there must be exactly one simulink model");
  }

  @Test
  @DisplayName("the round trip back over three hops keeps every metamodel")
  @SneakyThrows
  void test3() {
    Path folder = seededVsum();

    MigrationReport report = migrate(folder, MigrationSettings.defaults());

    assertTrue(report.migrated());
    assertTrue(report.sourceUpdate().attempted(), "the backflow runs over the whole chain");
    assertTrue(
        report.sourceUpdate().converged(),
        "three hops back to the brake system have to reach a fixed point");
    assertEquals(ALL_NS, nsUrisOnDisk(folder), "no metamodel is lost on the way back");
  }

  @Test
  @DisplayName("a port nothing derives survives the re-derivation of the simulink model")
  @SneakyThrows
  void test4() {
    Path folder = seededVsumWithHandWrittenPort();

    assertEquals(1, portCountIn(folder.resolve("example.simulink")), "the port was written");

    MigrationReport report =
        migrate(folder, MigrationSettings.forwardOnly().withPreservation(PreservationPolicy.USER));

    assertTrue(report.migrated());
    assertTrue(
        report.preservation().applied(),
        "the port has to be re-attached: " + report.preservation().lost());
    assertEquals(
        1,
        portCountIn(folder.resolve("example.simulink")),
        "the re-derived simulink model keeps the hand-written port");
  }

  @Test
  @DisplayName("the case study's reactions form a four-metamodel chain")
  void test5() {
    PropagationGraph graph = new PropagationGraph(BRAKE_CHAIN_SPECS);
    MetamodelNode brakesystem = graph.nodeContaining(BRAKESYSTEM_NS).orElseThrow();
    Set<MetamodelNode> present =
        Set.of(
            brakesystem,
            graph.nodeContaining(CAD_NS).orElseThrow(),
            graph.nodeContaining(SIMULINK_NS).orElseThrow(),
            graph.nodeContaining(AUTOSAR_NS).orElseThrow());

    assertEquals(
        present,
        graph.reachableFrom(brakesystem),
        "autosar sits three hops away and is still covered");

    DominancePlan plan =
        new MigrationPlanner(graph, new ExplicitDominance("brakesystem"))
            .plan(TestDominanceContexts.structuralOnly(graph, present));
    assertEquals(List.of(brakesystem), plan.sources());
    assertEquals(3, plan.derived().size());
  }

  private Path seededVsum() throws Exception {
    return BrakeSystemFixture.seededVsum(tempDir.resolve("brake-vsum"), BRAKE_CHAIN);
  }

  private Path seededVsumWithHandWrittenPort() throws Exception {
    Path folder = Files.createDirectories(tempDir.resolve("brake-vsum-with-port"));
    InternalVirtualModel vsum = BrakeSystemFixture.seed(folder, BRAKE_CHAIN);
    try (View view = Vsums.openViewOfAll(vsum, "write-port")) {
      CommittableView committable = view.withChangeRecordingTrait();
      InPort port = SimuLinkFactory.eINSTANCE.createInPort();
      port.setName("brakePressure");
      firstBlockIn(committable.getRootObjects()).getPorts().add(port);
      committable.commitChanges();
    } finally {
      vsum.dispose();
    }

    return folder;
  }
}
