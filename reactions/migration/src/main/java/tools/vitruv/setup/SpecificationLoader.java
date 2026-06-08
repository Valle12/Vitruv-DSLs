package tools.vitruv.setup;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import lombok.extern.slf4j.Slf4j;
import tools.vitruv.change.propagation.ChangePropagationSpecification;

@Slf4j
public class SpecificationLoader {
  private static final String CLASS_SUFFIX = ".class";
  private static final String MIR_PREFIX = "mir.";

  private SpecificationLoader() {}

  public static List<ChangePropagationSpecification> loadFromJar(Path propagationsJar)
      throws IOException {
    if (!Files.isRegularFile(propagationsJar)) {
      throw new IllegalArgumentException(
          "Specification jar not found: " + propagationsJar.toAbsolutePath());
    }

    URL jarUrl = propagationsJar.toUri().toURL();
    URLClassLoader loader =
        new URLClassLoader(new URL[] {jarUrl}, SpecificationLoader.class.getClassLoader());

    Map<Class<?>, ChangePropagationSpecification> candidates = new LinkedHashMap<>();
    try (JarFile jar = new JarFile(propagationsJar.toFile())) {
      for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements(); ) {
        String entry = entries.nextElement().getName();
        if (!entry.endsWith(CLASS_SUFFIX) || entry.contains("$") || entry.startsWith("META-INF")) {
          continue;
        }

        String className =
            entry.substring(0, entry.length() - CLASS_SUFFIX.length()).replace('/', '.');
        Class<?> candidate;
        try {
          candidate = Class.forName(className, false, loader);
        } catch (Throwable unresolvable) {
          continue;
        }

        if (!ChangePropagationSpecification.class.isAssignableFrom(candidate)
            || candidate.isInterface()
            || Modifier.isAbstract(candidate.getModifiers())) {
          continue;
        }

        try {
          Constructor<?> constructor = candidate.getDeclaredConstructor();
          candidates.put(candidate, (ChangePropagationSpecification) constructor.newInstance());
        } catch (Throwable ignored) {
        }
      }
    }

    Set<Class<?>> candidateClasses = candidates.keySet();
    List<Class<?>> leaves = new ArrayList<>();

    for (Class<?> candidateClass : candidateClasses) {
      boolean hasDerivedCandidate = false;
      for (Class<?> otherClass : candidateClasses) {
        if (otherClass != candidateClass && candidateClass.isAssignableFrom(otherClass)) {
          hasDerivedCandidate = true;
          break;
        }
      }

      if (!hasDerivedCandidate) {
        leaves.add(candidateClass);
      }
    }

    boolean hasApplicationSpecs =
        leaves.stream().anyMatch(candidate -> !candidate.getName().startsWith(MIR_PREFIX));
    List<ChangePropagationSpecification> specifications =
        leaves.stream()
            .filter(
                candidate -> !hasApplicationSpecs || !candidate.getName().startsWith(MIR_PREFIX))
            .map(candidates::get)
            .toList();

    if (specifications.isEmpty()) {
      throw new IllegalStateException(
          "No change propagation specification found in " + propagationsJar.toAbsolutePath());
    }

    log.info(
        "Loaded {} change propagation specification(s) from {}: {}",
        specifications.size(),
        propagationsJar.getFileName(),
        specifications.stream().map(spec -> spec.getClass().getSimpleName()).toList());
    return specifications;
  }
}
