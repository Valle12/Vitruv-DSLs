package tools.vitruv;

import java.nio.file.Path;
import java.util.List;
import tools.vitruv.applications.util.temporary.java.JavaSetup;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

public class Main {
  public static void main(String[] args) {
    JavaSetup.prepareFactories();
    JavaSetup.resetClasspathAndRegisterStandardLibrary();

    InternalVirtualModel internalVirtualModel = new VirtualModelBuilder().withStorageFolder(Path.of("E:\\projects\\Vitruv-DSLs\\reactions\\migration\\src\\main\\resources\\model")).withChangePropagationSpecification(List.of());
  }
}
