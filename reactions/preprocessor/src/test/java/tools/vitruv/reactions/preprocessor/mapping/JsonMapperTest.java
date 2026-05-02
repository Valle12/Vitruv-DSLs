package tools.vitruv.reactions.preprocessor.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;

class JsonMapperTest {
  private JsonMapper jsonMapper;

  @BeforeEach
  void setup() {
    jsonMapper = new JsonMapper();
  }

  @Test
  @DisplayName("Test with empty List")
  void test1() {
    List<String> result = jsonMapper.fromJsonToList("[]", new TypeReference<>() {});

    assertEquals(0, result.size());
  }

  @Test
  @DisplayName("Test with list of Strings")
  void test2() {
    List<String> result =
        jsonMapper.fromJsonToList("[\"hello\", \"world\"]", new TypeReference<List<String>>() {});

    assertEquals(2, result.size());
  }
}
