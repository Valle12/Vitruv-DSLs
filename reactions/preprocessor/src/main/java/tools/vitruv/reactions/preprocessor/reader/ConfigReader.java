package tools.vitruv.reactions.preprocessor.reader;

import java.nio.file.Path;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.vitruv.reactions.preprocessor.mapping.JsonMapper;

/** Reads the feature names a configuration selects, which is a flat JSON array of names. */
@Slf4j
public class ConfigReader {
  private final JsonMapper jsonMapper;
  private final Path config;

  /**
   * Creates a reader for the given configuration file.
   *
   * @param jsonMapper the mapper to deserialize the file with
   * @param config the path of the configuration file
   */
  public ConfigReader(JsonMapper jsonMapper, String config) {
    this.jsonMapper = jsonMapper;
    this.config = Path.of(config);
  }

  /**
   * Reads the feature names the configuration selects.
   *
   * @return the selected feature names, empty when the file is missing or holds no text
   */
  public List<String> readConfig() {
    Optional<String> optionalContent = TextReader.readTextFile(config);
    if (optionalContent.isEmpty()) {
      return List.of();
    }

    String content = optionalContent.get();
    if (content.isEmpty()) {
      return List.of();
    }

    return jsonMapper.fromJsonToList(content, new TypeReference<>() {});
  }
}
