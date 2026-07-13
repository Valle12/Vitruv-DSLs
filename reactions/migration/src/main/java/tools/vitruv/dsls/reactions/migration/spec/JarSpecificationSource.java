package tools.vitruv.dsls.reactions.migration.spec;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.vitruv.change.propagation.ChangePropagationSpecification;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class JarSpecificationSource implements SpecificationSource {
  private static final String CLASS_SUFFIX = ".class";
  private static final String GENERATED_PREFIX = "mir.";
  private final List<Constructor<?>> specificationConstructors;

  public static JarSpecificationSource fromJar(Path propagationsJar) throws IOException {
    if (!Files.isRegularFile(propagationsJar)) {
      throw new IllegalArgumentException(
          "Specification jar not found: " + propagationsJar.toAbsolutePath());
    }

    List<Class<?>> candidates = instantiableSpecificationClasses(propagationsJar);
    List<Class<?>> specificationClasses = preferApplicationLevelSpecs(leafClasses(candidates));
    if (specificationClasses.isEmpty()) {
      throw new IllegalStateException(
          "No change propagation specification found in " + propagationsJar.toAbsolutePath());
    }

    log.info(
        "Discovered {} change propagation specification(s) in {}: {}",
        specificationClasses.size(),
        propagationsJar.getFileName(),
        specificationClasses.stream().map(Class::getSimpleName).toList());
    return new JarSpecificationSource(
        specificationClasses.stream()
            .<Constructor<?>>map(JarSpecificationSource::noArgConstructor)
            .toList());
  }

  private static List<Class<?>> instantiableSpecificationClasses(Path propagationsJar)
      throws IOException {
    URL jarUrl = propagationsJar.toUri().toURL();
    URLClassLoader loader =
        new URLClassLoader(new URL[] {jarUrl}, JarSpecificationSource.class.getClassLoader());

    List<Class<?>> candidates = new ArrayList<>();
    try (JarFile jar = new JarFile(propagationsJar.toFile())) {
      for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements(); ) {
        String entry = entries.nextElement().getName();
        if (!entry.endsWith(CLASS_SUFFIX) || entry.contains("$") || entry.startsWith("META-INF")) {
          continue;
        }

        classIfInstantiableSpecification(entry, loader).ifPresent(candidates::add);
      }
    }

    return candidates;
  }

  private static Optional<Class<?>> classIfInstantiableSpecification(
      String jarEntry, ClassLoader loader) {
    String className =
        jarEntry.substring(0, jarEntry.length() - CLASS_SUFFIX.length()).replace('/', '.');
    try {
      Class<?> candidate = Class.forName(className, false, loader);
      if (!ChangePropagationSpecification.class.isAssignableFrom(candidate)
          || candidate.isInterface()
          || Modifier.isAbstract(candidate.getModifiers())) {
        return Optional.empty();
      }

      freshInstance(candidate.getDeclaredConstructor());
      return Optional.of(candidate);
    } catch (Throwable notLoadableOrInstantiable) {
      return Optional.empty();
    }
  }

  private static List<Class<?>> leafClasses(List<Class<?>> candidates) {
    return candidates.stream()
        .filter(
            candidate ->
                candidates.stream()
                    .noneMatch(other -> other != candidate && candidate.isAssignableFrom(other)))
        .toList();
  }

  private static List<Class<?>> preferApplicationLevelSpecs(List<Class<?>> leaves) {
    boolean hasApplicationSpecs =
        leaves.stream().anyMatch(leaf -> !leaf.getName().startsWith(GENERATED_PREFIX));
    return leaves.stream()
        .filter(leaf -> !hasApplicationSpecs || !leaf.getName().startsWith(GENERATED_PREFIX))
        .toList();
  }

  private static Constructor<?> noArgConstructor(Class<?> specificationClass) {
    try {
      return specificationClass.getDeclaredConstructor();
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(
          specificationClass + " lost its no-arg constructor between scan and use", e);
    }
  }

  private static ChangePropagationSpecification freshInstance(Constructor<?> constructor) {
    try {
      return (ChangePropagationSpecification) constructor.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(
          "Could not instantiate " + constructor.getDeclaringClass().getName(), e);
    }
  }

  @Override
  public List<ChangePropagationSpecification> createSpecifications() {
    return specificationConstructors.stream().map(JarSpecificationSource::freshInstance).toList();
  }
}
