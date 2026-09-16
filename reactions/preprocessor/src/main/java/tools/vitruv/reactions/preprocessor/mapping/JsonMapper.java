package tools.vitruv.reactions.preprocessor.mapping;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Deserializes JSON into the type a {@link TypeReference} describes. */
public class JsonMapper {
  private final ObjectMapper mapper = new ObjectMapper();

  /**
   * Deserializes the given JSON.
   *
   * @param <T> the type to deserialize into
   * @param json the JSON text
   * @param typeReference the target type, which may carry generic arguments
   * @return the deserialized value
   */
  public <T> T fromJsonToList(String json, TypeReference<T> typeReference) {
    return mapper.readValue(json, typeReference);
  }
}
