package tools.vitruv.reactions.preprocessor.reader;

import java.nio.file.Path;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.vitruv.reactions.preprocessor.mapping.JsonMapper;

@Slf4j
public class ConfigReader {
  private final JsonMapper jsonMapper;
  private final Path config;

  public ConfigReader(JsonMapper jsonMapper, String config) {
    this.jsonMapper = jsonMapper;
    this.config = Path.of(config);
  }

  public List<String> readConfig() {
    Optional<String> optionalContent = TextReader.readTextFile(config);
    if (optionalContent.isEmpty()) return List.of();
    String content = optionalContent.get();
    return jsonMapper.fromJsonToList(content, new TypeReference<>() {});
  }
}
