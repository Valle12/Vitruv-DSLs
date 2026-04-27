package tools.vitruv.reactions.preprocessor.mapping;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public class JsonMapper {
  private final ObjectMapper mapper = new ObjectMapper();

  public <T> T fromJsonToList(String json, TypeReference<T> typeReference) {
    return mapper.readValue(json, typeReference);
  }
}
