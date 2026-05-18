package tools.vitruv.reactions.preprocessor.extractor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.vitruv.reactions.preprocessor.mapping.BlockMapper;
import tools.vitruv.reactions.preprocessor.model.CodeBlocks;
import tools.vitruv.reactions.preprocessor.model.Header;
import tools.vitruv.reactions.preprocessor.model.ReactionsFile;
import tools.vitruv.reactions.preprocessor.reader.ConfigReader;
import tools.vitruv.reactions.preprocessor.reader.ReactionsReader;

@ExtendWith(MockitoExtension.class)
class FeatureExtractorTest {
  private static final String DUMMY_CONFIG = "test/config.json";
  private static final String DUMMY_DIR = "test/reactions";
  private static final Path EXPECTED_CONFIG_DIR = Path.of("test");
  private static final Path EXPECTED_OUTPUT_DIR = EXPECTED_CONFIG_DIR.resolve("config-reactions");
  private static final Path EXPECTED_OUTPUT_FILE = EXPECTED_OUTPUT_DIR.resolve("output.txt");
  private static final Path DUMMY_DELETE_FILE = EXPECTED_OUTPUT_DIR.resolve("oldFile.txt");

  @Mock(answer = Answers.CALLS_REAL_METHODS)
  private MockedStatic<Files> filesMock;

  @Test
  @DisplayName("Test creation of directory fails")
  void test1() {
    FeatureExtractor extractor = new FeatureExtractor(DUMMY_CONFIG, DUMMY_DIR);
    filesMock.when(() -> Files.exists(EXPECTED_OUTPUT_DIR)).thenReturn(false);
    filesMock.when(() -> Files.createDirectory(EXPECTED_OUTPUT_DIR)).thenThrow(IOException.class);

    extractor.extractFeatures();

    filesMock.verify(() -> Files.createDirectory(EXPECTED_OUTPUT_DIR));
  }

  @Test
  @DisplayName("Test if retrieving files or directories fails")
  @SuppressWarnings("resource")
  void test2() {
    FeatureExtractor extractor = new FeatureExtractor(DUMMY_CONFIG, DUMMY_DIR);
    filesMock.when(() -> Files.exists(eq(EXPECTED_OUTPUT_DIR))).thenReturn(true);
    filesMock.when(() -> Files.walk(eq(EXPECTED_OUTPUT_DIR))).thenThrow(IOException.class);

    extractor.extractFeatures();

    filesMock.verify(() -> Files.walk(eq(EXPECTED_OUTPUT_DIR)));
  }

  @Test
  @DisplayName("Test if deleting files or directory fails")
  @SuppressWarnings("resource")
  void test3() {
    FeatureExtractor extractor = new FeatureExtractor(DUMMY_CONFIG, DUMMY_DIR);
    filesMock.when(() -> Files.exists(eq(EXPECTED_OUTPUT_DIR))).thenReturn(true);
    filesMock
        .when(() -> Files.walk(eq(EXPECTED_OUTPUT_DIR)))
        .thenReturn(Stream.of(DUMMY_DELETE_FILE));
    filesMock.when(() -> Files.delete(eq(DUMMY_DELETE_FILE))).thenThrow(IOException.class);

    extractor.extractFeatures();

    filesMock.verify(() -> Files.delete(eq(DUMMY_DELETE_FILE)));
  }

  @Test
  @DisplayName("Test file creation fails")
  void test4() {
    ReactionsFile mockReactionsFile = mock(ReactionsFile.class);
    CodeBlocks mockBlocks = mock(CodeBlocks.class);
    when(mockReactionsFile.header()).thenReturn(new Header("dummy", ""));
    when(mockReactionsFile.codeBlocks()).thenReturn(mockBlocks);

    try (MockedConstruction<ConfigReader> ignored = mockConstruction(ConfigReader.class);
        MockedConstruction<ReactionsReader> ignored1 =
            mockConstruction(
                ReactionsReader.class,
                (mock, ctx) ->
                    when(mock.readReactionsDir())
                        .thenReturn(Map.of("output.txt", "dummyContent")));
        MockedConstruction<BlockMapper> ignored2 =
            mockConstruction(
                BlockMapper.class,
                (mock, ctx) ->
                    when(mock.extractBlocks(anyString())).thenReturn(mockReactionsFile))) {

      filesMock.when(() -> Files.exists(eq(EXPECTED_OUTPUT_DIR))).thenReturn(false);
      filesMock
          .when(() -> Files.createDirectory(eq(EXPECTED_OUTPUT_DIR)))
          .thenReturn(EXPECTED_OUTPUT_DIR);
      filesMock
          .when(() -> Files.createDirectories(eq(EXPECTED_OUTPUT_DIR)))
          .thenReturn(EXPECTED_OUTPUT_DIR);
      filesMock.when(() -> Files.createFile(eq(EXPECTED_OUTPUT_FILE))).thenThrow(IOException.class);

      FeatureExtractor extractor = new FeatureExtractor(DUMMY_CONFIG, DUMMY_DIR);
      extractor.extractFeatures();

      filesMock.verify(() -> Files.createFile(eq(EXPECTED_OUTPUT_FILE)));
    }
  }

  @Test
  @DisplayName("Test writing content to file fails")
  void test5() {
    ReactionsFile mockReactionsFile = mock(ReactionsFile.class);
    CodeBlocks mockBlocks = mock(CodeBlocks.class);
    when(mockReactionsFile.header()).thenReturn(new Header("dummy", ""));
    when(mockReactionsFile.codeBlocks()).thenReturn(mockBlocks);
    when(mockBlocks.reactions()).thenReturn(Map.of("feat1", List.of("content")));

    try (MockedConstruction<ConfigReader> ignored =
            mockConstruction(
                ConfigReader.class,
                (mock, ctx) -> when(mock.readConfig()).thenReturn(List.of("feat1")));
        MockedConstruction<ReactionsReader> ignored1 =
            mockConstruction(
                ReactionsReader.class,
                (mock, ctx) ->
                    when(mock.readReactionsDir())
                        .thenReturn(Map.of("output.txt", "dummyContent")));
        MockedConstruction<BlockMapper> ignored2 =
            mockConstruction(
                BlockMapper.class,
                (mock, ctx) ->
                    when(mock.extractBlocks(anyString())).thenReturn(mockReactionsFile))) {

      filesMock.when(() -> Files.exists(eq(EXPECTED_OUTPUT_DIR))).thenReturn(false);
      filesMock
          .when(() -> Files.createDirectory(eq(EXPECTED_OUTPUT_DIR)))
          .thenReturn(EXPECTED_OUTPUT_DIR);
      filesMock
          .when(() -> Files.createDirectories(eq(EXPECTED_OUTPUT_DIR)))
          .thenReturn(EXPECTED_OUTPUT_DIR);
      filesMock
          .when(() -> Files.createFile(eq(EXPECTED_OUTPUT_FILE)))
          .thenReturn(EXPECTED_OUTPUT_FILE);

      filesMock
          .when(
              () ->
                  Files.writeString(
                      eq(EXPECTED_OUTPUT_FILE), anyString(), eq(StandardCharsets.UTF_8)))
          .thenThrow(IOException.class);

      FeatureExtractor extractor = new FeatureExtractor(DUMMY_CONFIG, DUMMY_DIR);
      extractor.extractFeatures();

      filesMock.verify(
          () ->
              Files.writeString(eq(EXPECTED_OUTPUT_FILE), anyString(), eq(StandardCharsets.UTF_8)));
    }
  }

  // TODO potentially also use @TempDir to write the files and no mocking in here
  @SuppressWarnings("resource")
  @Test
  @DisplayName("Test successful execution")
  void test6() {
    Map<String, List<String>> reactions = new HashMap<>();
    reactions.put("feat1", List.of("call routA and unknown"));
    reactions.put("feat2", List.of("should be ignored"));

    Map<String, String> routines = new HashMap<>();
    routines.put("routA", "call routB");
    routines.put("routB", "call routA");

    ReactionsFile reactionsFile =
        new ReactionsFile(
            new Header("dummyReactions", "HEADER_CONTENT\n"), new CodeBlocks(reactions, routines));

    try (MockedConstruction<ConfigReader> ignored =
            mockConstruction(
                ConfigReader.class,
                (mock, ctx) -> when(mock.readConfig()).thenReturn(List.of("feat1")));
        MockedConstruction<ReactionsReader> ignored1 =
            mockConstruction(
                ReactionsReader.class,
                (mock, ctx) ->
                    when(mock.readReactionsDir())
                        .thenReturn(Map.of("output.txt", "dummyContent")));
        MockedConstruction<BlockMapper> ignored2 =
            mockConstruction(
                BlockMapper.class,
                (mock, ctx) -> when(mock.extractBlocks(anyString())).thenReturn(reactionsFile))) {

      filesMock.when(() -> Files.exists(eq(EXPECTED_OUTPUT_DIR))).thenReturn(true);
      filesMock
          .when(() -> Files.walk(eq(EXPECTED_OUTPUT_DIR)))
          .thenReturn(Stream.of(DUMMY_DELETE_FILE));
      filesMock.when(() -> Files.delete(eq(DUMMY_DELETE_FILE))).thenAnswer(i -> null);
      filesMock
          .when(() -> Files.createDirectory(eq(EXPECTED_OUTPUT_DIR)))
          .thenReturn(EXPECTED_OUTPUT_DIR);
      filesMock
          .when(() -> Files.createDirectories(eq(EXPECTED_OUTPUT_DIR)))
          .thenReturn(EXPECTED_OUTPUT_DIR);
      filesMock
          .when(() -> Files.createFile(eq(EXPECTED_OUTPUT_FILE)))
          .thenReturn(EXPECTED_OUTPUT_FILE);
      filesMock
          .when(
              () ->
                  Files.writeString(
                      eq(EXPECTED_OUTPUT_FILE), anyString(), eq(StandardCharsets.UTF_8)))
          .thenReturn(EXPECTED_OUTPUT_FILE);

      FeatureExtractor extractor = new FeatureExtractor(DUMMY_CONFIG, DUMMY_DIR);
      extractor.extractFeatures();

      filesMock.verify(() -> Files.delete(eq(DUMMY_DELETE_FILE)));
      filesMock.verify(() -> Files.createDirectory(eq(EXPECTED_OUTPUT_DIR)));
      filesMock.verify(() -> Files.createDirectories(eq(EXPECTED_OUTPUT_DIR)));
      filesMock.verify(() -> Files.createFile(eq(EXPECTED_OUTPUT_FILE)));

      ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
      filesMock.verify(
          () ->
              Files.writeString(
                  eq(EXPECTED_OUTPUT_FILE), stringCaptor.capture(), eq(StandardCharsets.UTF_8)));

      String writtenContent = stringCaptor.getValue();
      assertTrue(writtenContent.contains("HEADER_CONTENT"));
      assertTrue(writtenContent.contains("call routA and unknown"));
      assertTrue(writtenContent.contains("call routB"));
    }
  }
}
