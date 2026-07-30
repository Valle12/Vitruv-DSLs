package tools.vitruv.dsls.reactions.migration.spec;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ConfigurationScopedClassLoader extends URLClassLoader {
  static {
    ClassLoader.registerAsParallelCapable();
  }

  private final List<String> childFirstPrefixes;

  ConfigurationScopedClassLoader(URL[] urls, ClassLoader parent, Collection<String> prefixes) {
    super(urls, parent);
    this.childFirstPrefixes = List.copyOf(prefixes);
  }

  @Override
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
      Class<?> loaded = findLoadedClass(name);
      if (loaded == null) {
        loaded = isConfigurationScoped(name) ? loadChildFirst(name) : super.loadClass(name, false);
      }
      if (resolve) {
        resolveClass(loaded);
      }

      return loaded;
    }
  }

  private Class<?> loadChildFirst(String name) throws ClassNotFoundException {
    try {
      return findClass(name);
    } catch (ClassNotFoundException notInThisJar) {
      log.warn(
          "{} belongs to the rule configuration but is absent from the specification jar;"
              + " falling back to the class of the same name already on the classpath",
          name);
      return super.loadClass(name, false);
    }
  }

  private boolean isConfigurationScoped(String name) {
    return childFirstPrefixes.stream().anyMatch(name::startsWith);
  }
}
