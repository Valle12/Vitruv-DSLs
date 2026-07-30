package tools.vitruv.dsls.reactions.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {
  @TempDir Path tempDir;

  @Test
  @DisplayName("help returns zero")
  void test1() {
    assertEquals(0, Main.run(new String[] {"--help"}));
    assertEquals(0, Main.run(new String[] {"-h"}));
  }

  @Test
  @DisplayName("missing model option fails")
  void test2() {
    assertEquals(1, Main.run(new String[] {}));
    assertEquals(1, Main.run(new String[] {"--model"}));
  }

  @Test
  @DisplayName("nonexistent model folder fails")
  void test3() {
    Path missing = tempDir.resolve("does-not-exist");
    assertEquals(1, Main.run(new String[] {"-m", missing.toString(), "-p", "specs.jar"}));
  }

  @Test
  @DisplayName("missing propagations option fails")
  void test4() {
    assertEquals(1, Main.run(new String[] {"-m", tempDir.toString()}));
    assertEquals(1, Main.run(new String[] {"--model", tempDir.toString(), "--propagations"}));
  }

  @Test
  @DisplayName("unknown strategy fails")
  void test5() {
    assertEquals(
        1, Main.run(new String[] {"-m", tempDir.toString(), "-p", "specs.jar", "-s", "bogus"}));
  }

  @Test
  @DisplayName("explicit strategy without dominant fails")
  void test6() {
    assertEquals(1, Main.run(new String[] {"-m", tempDir.toString(), "-p", "specs.jar"}));
  }

  @Test
  @DisplayName("unknown migration mode fails")
  void test8() {
    assertEquals(
        1,
        Main.run(
            new String[] {
              "-m", tempDir.toString(), "-p", "specs.jar", "-d", "uml", "--mode", "bogus"
            }));
  }

  @Test
  @DisplayName("nonexistent specification jar fails")
  void test7() {
    Path missingJar = tempDir.resolve("missing.jar");
    assertEquals(
        1,
        Main.run(
            new String[] {"-m", tempDir.toString(), "-p", missingJar.toString(), "-d", "uml"}));
  }
}
