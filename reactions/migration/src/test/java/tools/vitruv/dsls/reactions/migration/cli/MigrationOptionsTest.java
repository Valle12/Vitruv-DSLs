package tools.vitruv.dsls.reactions.migration.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.dsls.reactions.migration.strategy.StrategyChoice;

class MigrationOptionsTest {
  @TempDir Path tempDir;

  private MigrationOptions parse(String... args) {
    return MigrationOptions.parse(new CommandLine(args));
  }

  @Test
  @DisplayName("parses a minimal explicit invocation with defaults")
  void test1() {
    MigrationOptions options = parse("-m", tempDir.toString(), "-p", "specs.jar", "-d", "uml");

    assertEquals(tempDir, options.vsumFolder());
    assertEquals(Path.of("specs.jar"), options.specificationsJar());
    assertEquals(StrategyChoice.EXPLICIT, options.strategy());
    assertEquals(Optional.of("uml"), options.dominantToken());
    assertEquals(SourceUpdateMode.FIXPOINT, options.sourceUpdate());
    assertEquals(MigrationOptions.DEFAULT_MAX_ROUNDS, options.maxSourceUpdateRounds());
    assertFalse(options.backup());
  }

  @Test
  @DisplayName("missing model fails")
  void test2() {
    assertThrows(UsageException.class, () -> parse("-p", "specs.jar"));
    assertThrows(UsageException.class, () -> parse("--model"));
  }

  @Test
  @DisplayName("nonexistent model folder fails")
  void test3() {
    String missing = tempDir.resolve("does-not-exist").toString();

    assertThrows(UsageException.class, () -> parse("-m", missing, "-p", "specs.jar", "-d", "uml"));
  }

  @Test
  @DisplayName("missing propagations fails")
  void test4() {
    String dir = tempDir.toString();
    assertThrows(UsageException.class, () -> parse("-m", dir, "-d", "uml"));
  }

  @Test
  @DisplayName("explicit strategy without dominant token fails")
  void test5() {
    String dir = tempDir.toString();
    assertThrows(UsageException.class, () -> parse("-m", dir, "-p", "specs.jar"));
  }

  @Test
  @DisplayName("structural strategies need no dominant token")
  void test6() {
    MigrationOptions reachability =
        parse("-m", tempDir.toString(), "-p", "specs.jar", "-s", "reachability");
    MigrationOptions fewestChanges =
        parse("-m", tempDir.toString(), "-p", "specs.jar", "-s", "fewestChanges");

    assertEquals(StrategyChoice.REACHABILITY, reachability.strategy());
    assertEquals(StrategyChoice.FEWEST_CHANGES, fewestChanges.strategy());
  }

  @Test
  @DisplayName("unknown strategy fails")
  void test7() {
    String dir = tempDir.toString();
    assertThrows(UsageException.class, () -> parse("-m", dir, "-p", "specs.jar", "-s", "bogus"));
  }

  @Test
  @DisplayName("parses source update modes")
  void test8() {
    MigrationOptions none =
        parse("-m", tempDir.toString(), "-p", "specs.jar", "-d", "uml", "-u", "none");

    assertEquals(SourceUpdateMode.NONE, none.sourceUpdate());
    String dir = tempDir.toString();
    assertThrows(
        UsageException.class,
        () -> parse("-m", dir, "-p", "specs.jar", "-d", "uml", "-u", "bogus"));
  }

  @Test
  @DisplayName("parses max rounds")
  void test9() {
    String dir = tempDir.toString();
    MigrationOptions options = parse("-m", dir, "-p", "specs.jar", "-d", "uml", "-r", "5");

    assertEquals(5, options.maxSourceUpdateRounds());
    assertThrows(
        UsageException.class, () -> parse("-m", dir, "-p", "specs.jar", "-d", "uml", "-r", "abc"));
    assertThrows(
        UsageException.class, () -> parse("-m", dir, "-p", "specs.jar", "-d", "uml", "-r", "0"));
  }

  @Test
  @DisplayName("parses the backup flag")
  void test10() {
    MigrationOptions options =
        parse("-m", tempDir.toString(), "-p", "specs.jar", "-d", "uml", "-b");

    assertTrue(options.backup());
  }
}
