package tools.vitruv.dsls.reactions.migration.spec;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.vitruv.change.propagation.ChangePropagationSpecification;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class JarSpecificationSource implements SpecificationSource, Closeable {
  private static final String CLASS_SUFFIX = ".class";
  private static final String GENERATED_PREFIX = "mir.";
  private final List<Constructor<?>> specificationConstructors;
  private final ConfigurationScopedClassLoader loader;

  public static JarSpecificationSource fromJar(Path propagationsJar) throws IOException {
    return fromJar(propagationsJar, List.of());
  }

  public static JarSpecificationSource fromJar(
      Path propagationsJar, Collection<String> additionalChildFirstPrefixes) throws IOException {
    if (!Files.isRegularFile(propagationsJar)) {
      throw new IllegalArgumentException(
          "Specification jar not found: " + propagationsJar.toAbsolutePath());
    }

    URL jarUrl = propagationsJar.toUri().toURL();
    ClassLoader parent = JarSpecificationSource.class.getClassLoader();

    Set<String> childFirstPrefixes;
    List<String> discoveredNames;
    try (URLClassLoader discoveryLoader = new URLClassLoader(new URL[] {jarUrl}, parent)) {
      List<Class<?>> discovered = specificationClasses(propagationsJar, discoveryLoader);
      if (discovered.isEmpty()) {
        throw new IllegalStateException(
            "No change propagation specification found in " + propagationsJar.toAbsolutePath());
      }

      discoveredNames = discovered.stream().map(Class::getName).toList();
      childFirstPrefixes = configurationScopedPrefixes(discovered, additionalChildFirstPrefixes);
    }

    log.info(
        "Loading the rules of {} from the jar for {}",
        propagationsJar.getFileName(),
        childFirstPrefixes);

    ConfigurationScopedClassLoader loader =
        new ConfigurationScopedClassLoader(new URL[] {jarUrl}, parent, childFirstPrefixes);
    List<Class<?>> specificationClasses = instantiableSpecifications(discoveredNames, loader);
    if (specificationClasses.isEmpty()) {
      loader.close();
      throw new IllegalStateException(
          "The specifications of "
              + propagationsJar.toAbsolutePath()
              + " could not be loaded from the jar itself");
    }

    log.info(
        "Discovered {} change propagation specification(s) in {}: {}",
        specificationClasses.size(),
        propagationsJar.getFileName(),
        specificationClasses.stream().map(Class::getSimpleName).toList());
    return new JarSpecificationSource(
        specificationClasses.stream()
            .<Constructor<?>>map(JarSpecificationSource::noArgConstructor)
            .toList(),
        loader);
  }

  private static Set<String> configurationScopedPrefixes(
      List<Class<?>> discovered, Collection<String> additionalPrefixes) {
    Set<String> prefixes = new LinkedHashSet<>();
    prefixes.add(GENERATED_PREFIX);
    discovered.stream()
        .map(Class::getPackageName)
        .filter(packageName -> !packageName.isEmpty())
        .map(packageName -> packageName + ".")
        .forEach(prefixes::add);
    prefixes.addAll(additionalPrefixes);
    return prefixes;
  }

  private static List<Class<?>> specificationClasses(Path propagationsJar, ClassLoader loader)
      throws IOException {
    return preferApplicationLevelSpecs(
        leafClasses(instantiableSpecificationClasses(propagationsJar, loader)));
  }

  private static List<Class<?>> instantiableSpecificationClasses(
      Path propagationsJar, ClassLoader loader) throws IOException {
    List<Class<?>> candidates = new ArrayList<>();
    try (JarFile jar = new JarFile(propagationsJar.toFile())) {
      for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements(); ) {
        String entry = entries.nextElement().getName();
        if (!entry.endsWith(CLASS_SUFFIX) || entry.contains("$") || entry.startsWith("META-INF")) {
          continue;
        }

        String className =
            entry.substring(0, entry.length() - CLASS_SUFFIX.length()).replace('/', '.');
        classIfInstantiableSpecification(className, loader).ifPresent(candidates::add);
      }
    }

    return candidates;
  }

  private static List<Class<?>> instantiableSpecifications(
      List<String> classNames, ClassLoader loader) {
    return classNames.stream()
        .map(className -> classIfInstantiableSpecification(className, loader))
        .flatMap(Optional::stream)
        .toList();
  }

  private static Optional<Class<?>> classIfInstantiableSpecification(
      String className, ClassLoader loader) {
    try {
      Class<?> candidate = Class.forName(className, false, loader);
      if (!ChangePropagationSpecification.class.isAssignableFrom(candidate)
          || candidate.isInterface()
          || Modifier.isAbstract(candidate.getModifiers())) {
        return Optional.empty();
      }

      freshInstance(candidate.getDeclaredConstructor());
      return Optional.of(candidate);
    } catch (Exception | LinkageError notLoadableOrInstantiable) {
      // A jar class whose dependencies are missing fails with a LinkageError rather than an
      // exception, and either way it is not a specification this source can offer.
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
  public void close() throws IOException {
    loader.close();
  }

  @Override
  public List<ChangePropagationSpecification> createSpecifications() {
    return specificationConstructors.stream().map(JarSpecificationSource::freshInstance).toList();
  }
}
