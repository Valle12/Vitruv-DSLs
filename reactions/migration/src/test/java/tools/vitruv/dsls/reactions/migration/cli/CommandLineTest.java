package tools.vitruv.dsls.reactions.migration.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CommandLineTest {
  @Test
  @DisplayName("reads values by long and short name")
  void test1() {
    CommandLine commandLine = new CommandLine(new String[] {"--model", "vsum", "-p", "specs.jar"});

    assertEquals(
        Optional.of("vsum"), commandLine.value(CommandLine.MODEL, CommandLine.MODEL_SHORT));
    assertEquals(
        Optional.of("specs.jar"),
        commandLine.value(CommandLine.PROPAGATIONS, CommandLine.PROPAGATIONS_SHORT));
  }

  @Test
  @DisplayName("option without following value is absent")
  void test2() {
    CommandLine commandLine = new CommandLine(new String[] {"--model"});

    assertTrue(commandLine.value(CommandLine.MODEL, CommandLine.MODEL_SHORT).isEmpty());
  }

  @Test
  @DisplayName("detects flags and help")
  void test3() {
    CommandLine withFlags = new CommandLine(new String[] {"-b", "--help"});
    CommandLine withoutFlags = new CommandLine(new String[] {"--model", "vsum"});

    assertTrue(withFlags.flag(CommandLine.BACKUP, CommandLine.BACKUP_SHORT));
    assertTrue(withFlags.isHelpRequested());
    assertFalse(withoutFlags.flag(CommandLine.BACKUP, CommandLine.BACKUP_SHORT));
    assertFalse(withoutFlags.isHelpRequested());
  }

  @Test
  @DisplayName("usage documents every option")
  void test4() {
    String usage = CommandLine.usage();

    for (String option :
        new String[] {
          CommandLine.MODEL,
          CommandLine.PROPAGATIONS,
          CommandLine.STRATEGY,
          CommandLine.DOMINANT,
          CommandLine.SOURCE_UPDATE,
          CommandLine.MAX_ROUNDS,
          CommandLine.BACKUP,
          CommandLine.HELP
        }) {
      assertTrue(usage.contains(option), "usage must document " + option);
    }
  }
}
