package tools.vitruv.reactions.preprocessor.extractor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.vitruv.reactions.preprocessor.mapping.BlockMapper;
import tools.vitruv.reactions.preprocessor.mapping.JsonMapper;
import tools.vitruv.reactions.preprocessor.model.CodeBlocks;
import tools.vitruv.reactions.preprocessor.model.ReactionsFile;
import tools.vitruv.reactions.preprocessor.model.ReactionsUtils;
import tools.vitruv.reactions.preprocessor.reader.ConfigReader;
import tools.vitruv.reactions.preprocessor.reader.ReactionsReader;

@Slf4j
@RequiredArgsConstructor
public class FeatureExtractor {
  private static final String OUTPUT_DIR_SUFFIX = "-reactions";
  private static final String JSON_EXTENSION = ".json";
  private static final Pattern WORD_PATTERN = Pattern.compile("(\\w+)(?:\\.(\\w+))?");
  private static final Pattern UNQUALIFIED_IMPORT_PATTERN =
      Pattern.compile(
          "^\\s*import\\s+(?:routines\\s+)?(\\w+)[ \\t]*(?://[^\\n]*)?\\r?$", Pattern.MULTILINE);
  private final String configFile;
  private final String reactionsDir;
  private final Map<String, ReactionsUtils> reactionsUtils = new HashMap<>();

  public void extractFeatures() {
    JsonMapper jsonMapper = new JsonMapper();

    Path configPath = Path.of(configFile.substring(0, configFile.indexOf(JSON_EXTENSION)));
    Path configDir = configPath.getParent();
    Path outputDir = configDir.resolve(configPath.getFileName().toString() + OUTPUT_DIR_SUFFIX);

    try {
      deleteExistingOutputDir(outputDir);
      Files.createDirectory(outputDir);
    } catch (IOException e) {
      log.error("Error creating output directory");
      return;
    }

    ConfigReader configReader = new ConfigReader(jsonMapper, configFile);
    List<String> features = configReader.readConfig();

    ReactionsReader reactionsReader = new ReactionsReader(reactionsDir);
    Map<String, String> reactions = reactionsReader.readReactionsDir();

    createNewReactionsFiles(features, reactions, outputDir);
  }

  private void deleteExistingOutputDir(Path outputDir) {
    if (Files.exists(outputDir)) {
      try (Stream<Path> paths = Files.walk(outputDir).sorted(Comparator.reverseOrder())) {
        paths.forEach(
            path -> {
              try {
                Files.delete(path);
              } catch (IOException e) {
                log.error("Error deleting file or directory {}", path);
              }
            });
      } catch (IOException e) {
        log.error("Error retrieving files and dirs within {}", outputDir);
      }
    }
  }

  private void createNewReactionsFiles(
      List<String> features, Map<String, String> reactions, Path outputDir) {
    BlockMapper blockMapper = new BlockMapper();

    for (Map.Entry<String, String> entry : reactions.entrySet()) {
      ReactionsFile reactionsFile = blockMapper.extractBlocks(entry.getValue());
      Path outputFile = outputDir.resolve(entry.getKey());

      try {
        Files.createDirectories(outputFile.getParent());
        Files.createFile(outputFile);
      } catch (IOException e) {
        log.error("Error creating output file {}", outputFile);
      }

      Set<String> includedRoutines = new HashSet<>();
      StringBuilder sb = new StringBuilder();
      sb.append(reactionsFile.header().header());
      reactionsUtils.put(
          reactionsFile.header().reactionsName(),
          new ReactionsUtils(
              reactionsFile,
              outputFile,
              sb,
              includedRoutines,
              extractUnqualifiedImports(reactionsFile.header().header())));
    }

    for (Map.Entry<String, ReactionsUtils> entry : reactionsUtils.entrySet()) {
      CodeBlocks codeBlocks = entry.getValue().reactionsFile().codeBlocks();
      StringBuilder sb = entry.getValue().sb();

      for (Map.Entry<String, List<String>> featureMapping : codeBlocks.reactions().entrySet()) {
        if (!features.contains(featureMapping.getKey())) {
          continue;
        }

        for (String reaction : featureMapping.getValue()) {
          sb.append(reaction);
          sb.append("\n\n");
          appendRoutines(reaction, entry.getValue());
        }
      }
    }

    for (Map.Entry<String, ReactionsUtils> entry : reactionsUtils.entrySet()) {
      Path outputFile = entry.getValue().outputFile();
      StringBuilder sb = entry.getValue().sb();

      try {
        Files.writeString(outputFile, sb.toString(), StandardCharsets.UTF_8);
      } catch (IOException e) {
        log.error("Error writing to output file {}", outputFile);
      }
    }
  }

  private void appendRoutines(String content, ReactionsUtils current) {
    if (content == null) {
      return;
    }

    CodeBlocks codeBlocks = current.reactionsFile().codeBlocks();
    Set<String> includedRoutines = current.includedRoutines();
    StringBuilder sb = current.sb();
    Matcher matcher = WORD_PATTERN.matcher(content);

    while (matcher.find()) {
      String qualifiedName = matcher.group(2);
      String routineKey = qualifiedName == null ? matcher.group(1) : qualifiedName;
      appendLinkedRoutines(qualifiedName == null ? null : matcher.group(1), routineKey);

      if (includedRoutines.contains(routineKey)) {
        continue;
      }

      if (!codeBlocks.routines().containsKey(routineKey)) {
        appendImportedRoutine(routineKey, current);
      } else {
        String routine = codeBlocks.routines().get(routineKey);
        includedRoutines.add(routineKey);
        sb.append(routine);
        sb.append("\n\n");
        appendRoutines(routine, current);
      }
    }
  }

  private void appendLinkedRoutines(String linkedReaction, String routineKey) {
    if (linkedReaction == null) {
      return;
    }

    ReactionsUtils linkedReactionsUtils = reactionsUtils.get(linkedReaction);
    if (linkedReactionsUtils == null) {
      return;
    }

    String linkedRoutine =
        linkedReactionsUtils.reactionsFile().codeBlocks().routines().get(routineKey);
    appendRoutines(linkedRoutine, linkedReactionsUtils);
  }

  private void appendImportedRoutine(String routineKey, ReactionsUtils current) {
    for (String importedName : current.unqualifiedImports()) {
      ReactionsUtils imported = reactionsUtils.get(importedName);
      String routine =
          imported == null
              ? null
              : imported.reactionsFile().codeBlocks().routines().get(routineKey);
      if (imported == null || routine == null) {
        continue;
      }

      if (!imported.includedRoutines().contains(routineKey)) {
        imported.includedRoutines().add(routineKey);
        imported.sb().append(routine);
        imported.sb().append("\n\n");
        appendRoutines(routine, imported);
      }
      return;
    }
  }

  private List<String> extractUnqualifiedImports(String header) {
    List<String> imports = new ArrayList<>();
    Matcher matcher = UNQUALIFIED_IMPORT_PATTERN.matcher(header);

    while (matcher.find()) {
      imports.add(matcher.group(1));
    }

    return imports;
  }
}
