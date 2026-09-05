package tools.vitruv.dsls.reactions.migration.migration;

import brakesystem.BrakeDisk;
import brakesystem.Brakesystem;
import brakesystem.BrakesystemFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import mir.reactions.autosar2simulink.Autosar2simulinkChangePropagationSpecification;
import mir.reactions.brakesystem2cad.Brakesystem2cadChangePropagationSpecification;
import mir.reactions.cad2brakesystem.Cad2brakesystemChangePropagationSpecification;
import mir.reactions.cad2simulink.Cad2simulinkChangePropagationSpecification;
import mir.reactions.simulink2autosar.Simulink2autosarChangePropagationSpecification;
import mir.reactions.simulink2cad.Simulink2cadChangePropagationSpecification;
import org.eclipse.emf.common.util.URI;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.dsls.reactions.migration.spec.SpecificationSource;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

final class BrakeSystemFixture {
  static final String BRAKESYSTEM_NS = "http://www.example.org/brakesystem";
  static final String CAD_NS = "http://www.example.org/cad";
  static final String SIMULINK_NS = "http://vitruv.tools/methodologisttemplate/simulink";
  static final String AUTOSAR_NS = "http://vitruv.tools/methodologisttemplate/autosar";
  static final Set<String> ALL_NS = Set.of(BRAKESYSTEM_NS, CAD_NS, SIMULINK_NS, AUTOSAR_NS);
  static final int DIAMETER_IN_MM = 320;

  private BrakeSystemFixture() {}

  static SpecificationSource brakeChain() {
    return () ->
        List.of(
            new Brakesystem2cadChangePropagationSpecification(),
            new Cad2brakesystemChangePropagationSpecification(),
            new Cad2simulinkChangePropagationSpecification(),
            new Simulink2cadChangePropagationSpecification(),
            new Simulink2autosarChangePropagationSpecification(),
            new Autosar2simulinkChangePropagationSpecification());
  }

  static TestUserInteraction.ResultProvider answering() {
    return new TestUserInteraction.ResultProvider(LibraryCliFixture.permissiveInteraction("model"));
  }

  static Brakesystem frontAxle() {
    Brakesystem brakesystem = BrakesystemFactory.eINSTANCE.createBrakesystem();
    brakesystem.setInstanceName("FrontAxle");
    brakesystem.setSpecificationType("OE");
    BrakeDisk disk = BrakesystemFactory.eINSTANCE.createBrakeDisk();
    disk.setId("disk-1");
    disk.setOEM_number("34116792217");
    disk.setSpecificationType("OE");
    disk.setFittingPosition("front");
    disk.setDiameterInMM(DIAMETER_IN_MM);
    disk.setCenteringDiameterInMM(75);
    disk.setRimHoleNumber(5);
    disk.setHoleArrangementNumber(1);
    disk.setBoltHoleCircleInMM(120);
    disk.setBrakeDiskThicknessInMM(30);
    disk.setMinimumThicknessInMM(28);
    disk.setVentilated(true);
    brakesystem.getBrakeComponents().add(disk);
    return brakesystem;
  }

  @SuppressWarnings("UnstableApiUsage")
  static InternalVirtualModel seed(Path folder, SpecificationSource specifications)
      throws Exception {
    InternalVirtualModel vsum =
        Vsums.build(folder, specifications.createSpecifications(), answering());
    try (View view = Vsums.openEmptyView(vsum, "seed")) {
      CommittableView committable = view.withChangeDerivingTrait();
      committable.registerRoot(
          frontAxle(),
          URI.createFileURI(folder.resolve("example.brakesystem").toAbsolutePath().toString()));
      committable.commitChanges();
    }

    return vsum;
  }

  static Path seededVsum(Path folder, SpecificationSource specifications) throws Exception {
    Files.createDirectories(folder);
    seed(folder, specifications).dispose();
    return folder;
  }
}
