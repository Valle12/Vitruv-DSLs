package tools.vitruv.dsls.reactions.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.change.propagation.ConsistencyRuleId;
import tools.vitruv.dsls.reactions.migration.spec.JarSpecificationSource;

public final class PropagationJars {
  public static final String RESOURCE_FOLDER = "/propagations";
  public static final int CONFIGURATIONS = 9;

  private PropagationJars() {}

  public static Path config(int number) {
    if (number < 1 || number > CONFIGURATIONS) {
      throw new IllegalArgumentException(
          "No such configuration: " + number + ". There are 1 to " + CONFIGURATIONS + ".");
    }

    return jarNamed("umljava-config" + number + ".jar");
  }

  public static Path config1() {
    return config(1);
  }

  public static Path config7() {
    return config(7);
  }

  public static Set<String> ruleIdsOf(Path propagationsJar) throws IOException {
    return ruleHashesOf(propagationsJar).keySet().stream()
        .map(ConsistencyRuleId::value)
        .collect(Collectors.toUnmodifiableSet());
  }

  public static Map<ConsistencyRuleId, String> ruleHashesOf(Path propagationsJar)
      throws IOException {
    Map<ConsistencyRuleId, String> hashes = new HashMap<>();
    try (JarSpecificationSource source = JarSpecificationSource.fromJar(propagationsJar)) {
      for (ChangePropagationSpecification specification : source.createSpecifications()) {
        hashes.putAll(specification.getConsistencyRuleHashes());
      }
    }

    return Map.copyOf(hashes);
  }

  private static Path jarNamed(String fileName) {
    var url = PropagationJars.class.getResource(RESOURCE_FOLDER + "/" + fileName);
    if (url == null) {
      throw new IllegalStateException(
          fileName
              + " is not on the test classpath; it is committed at src/test/resources"
              + RESOURCE_FOLDER
              + " - see that folder's README.md to rebuild it");
    }

    try {
      Path jar = Path.of(url.toURI());
      if (!Files.isRegularFile(jar)) {
        throw new IllegalStateException("not a readable jar: " + jar);
      }

      return jar;
    } catch (java.net.URISyntaxException e) {
      throw new IllegalStateException("could not resolve " + url, e);
    }
  }
}
