package tools.vitruv.reactions.preprocessor.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.vitruv.reactions.preprocessor.mapping.JsonMapper;

class ConfigReaderTest {
  private final JsonMapper jsonMapper = new JsonMapper();
  private ConfigReader configReader;

  @Test
  @DisplayName("Test with invalid config path")
  void test1() {
    Path path = Path.of("invalid/config");
    configReader = new ConfigReader(jsonMapper, path.toString());

    List<String> result = configReader.readConfig();

    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("Test with empty config file")
  @SneakyThrows
  void test2() {
    Path path =
        Path.of(
            Objects.requireNonNull(getClass().getClassLoader().getResource("emptyConfig.json"))
                .toURI());
    configReader = new ConfigReader(jsonMapper, path.toString());

    List<String> result = configReader.readConfig();

    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("Test with config without features")
  @SneakyThrows
  void test3() {
    Path path =
        Path.of(
            Objects.requireNonNull(getClass().getClassLoader().getResource("noFeatures.json"))
                .toURI());
    configReader = new ConfigReader(jsonMapper, path.toString());

    List<String> result = configReader.readConfig();

    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("Test with valid config")
  @SneakyThrows
  void test4() {
    Path path =
        Path.of(
            Objects.requireNonNull(getClass().getClassLoader().getResource("validConfig.json"))
                .toURI());
    configReader = new ConfigReader(jsonMapper, path.toString());

    List<String> result = configReader.readConfig();

    assertEquals(2, result.size());
    assertEquals("hello", result.get(0));
    assertEquals("world", result.get(1));
  }
}
