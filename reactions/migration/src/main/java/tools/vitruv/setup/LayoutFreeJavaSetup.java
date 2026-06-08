package tools.vitruv.setup;

import tools.vitruv.applications.util.temporary.java.JavaSetup;

public class LayoutFreeJavaSetup {
  private LayoutFreeJavaSetup() {}

  public static void prepareFactories() {
    JavaSetup.prepareFactories(() -> LayoutFreeJavaResource::new);
  }
}
