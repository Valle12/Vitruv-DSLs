package tools.vitruv.dsls.reactions.migration.adapter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.emftext.language.java.JavaClasspath;
import org.emftext.language.java.classifiers.ConcreteClassifier;
import org.emftext.language.java.containers.CompilationUnit;
import org.emftext.language.java.containers.Package;
import org.emftext.language.java.resource.java.IJavaOptions;
import tools.vitruv.applications.util.temporary.java.JavaSetup;

public class JavaModelAdapter implements MetamodelAdapter {
  @SuppressWarnings("HttpUrlsUsage")
  private static final String JAMOPP_NS_PREFIX = "http://www.emftext.org/java";

  private static final String CLASSPATH_CACHE_SCHEME = "cache";
  private static final String JAVA_FILE_EXTENSION = "java";
  private static final Set<String> PLATFORM_PACKAGE_ROOTS = Set.of("java", "javax", "sun", "jdk");

  private static void rebindClasspathReferences(EObject holder, JavaClasspath liveClasspath) {
    for (EReference feature : holder.eClass().getEAllReferences()) {
      if (!feature.isContainment() && !feature.isContainer() && feature.isChangeable()) {
        rebindFeature(holder, feature, liveClasspath);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static void rebindFeature(
      EObject holder, EReference feature, JavaClasspath liveClasspath) {
    if (feature.isMany()) {
      List<EObject> values = (List<EObject>) holder.eGet(feature);
      for (int i = 0; i < values.size(); i++) {
        EObject equivalent = classpathEquivalent(values.get(i), liveClasspath);
        if (equivalent != null) {
          values.set(i, equivalent);
        }
      }
    } else if (holder.eGet(feature) instanceof EObject target) {
      EObject equivalent = classpathEquivalent(target, liveClasspath);
      if (equivalent != null) {
        holder.eSet(feature, equivalent);
      }
    }
  }

  private static EObject classpathEquivalent(EObject target, JavaClasspath liveClasspath) {
    if (!(target instanceof ConcreteClassifier classifier) || target.eIsProxy()) {
      return null;
    }

    Resource resource = target.eResource();
    if (resource == null
        || resource.getURI() == null
        || !CLASSPATH_CACHE_SCHEME.equals(resource.getURI().scheme())) {
      return null;
    }

    EObject equivalent = liveClasspath.getClassifier(classifier.getQualifiedName());
    return equivalent == target ? null : equivalent;
  }

  @Override
  public boolean handles(String nsUri) {
    return nsUri.startsWith(JAMOPP_NS_PREFIX);
  }

  @Override
  public void prepareStandalone() {
    JavaSetup.prepareFactories(() -> LayoutFreeJavaResource::new);
    JavaSetup.resetClasspathAndRegisterStandardLibrary();
  }

  @Override
  public Map<Object, Object> loadOptions() {
    return Map.of(IJavaOptions.DISABLE_LAYOUT_INFORMATION_RECORDING, Boolean.TRUE);
  }

  @Override
  public void dropResolutionCaches(EObject root) {
    if (root instanceof Package javaPackage) {
      javaPackage.getCompilationUnits().clear();
    }
  }

  @Override
  public void prepareForReplayInto(ResourceSet liveResourceSet, Iterable<EObject> detachedRoots) {
    JavaClasspath liveClasspath = JavaClasspath.get(liveResourceSet);
    for (EObject root : detachedRoots) {
      rebindClasspathReferences(root, liveClasspath);
      root.eAllContents()
          .forEachRemaining(object -> rebindClasspathReferences(object, liveClasspath));
    }
  }

  @Override
  public void normalizeLoadedResource(Resource resource) {
    if (resource.getURI() == null) {
      return;
    }

    for (EObject root : resource.getContents()) {
      if (root instanceof CompilationUnit unit && unit.getName() == null) {
        unit.setName(resource.getURI().lastSegment());
      }
    }
  }

  @Override
  public boolean refreshSerializedForm(Resource resource) {
    URI uri = resource.getURI();
    if (uri == null || !uri.isFile() || !claimsResource(uri) || resource.getContents().isEmpty()) {
      return false;
    }

    Path file = Path.of(uri.toFileString());
    byte[] serialized;
    try {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      resource.save(buffer, null);
      serialized = buffer.toByteArray();
    } catch (IOException e) {
      return false;
    }

    try {
      if (Files.exists(file) && Arrays.equals(Files.readAllBytes(file), serialized)) {
        return false;
      }

      Files.createDirectories(file.getParent());
      Files.write(file, serialized);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not rewrite " + file + " from its model", e);
    }

    resource.setModified(false);
    return true;
  }

  @Override
  public boolean requiresChangeRecording() {
    return true;
  }

  @Override
  public Optional<String> externalIdentityOf(EObject element) {
    if (!(element instanceof ConcreteClassifier classifier) || element.eIsProxy()) {
      return Optional.empty();
    }

    try {
      return Optional.ofNullable(classifier.getQualifiedName()).filter(name -> !name.isBlank());
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  @Override
  public boolean claimsResource(URI uri) {
    return JAVA_FILE_EXTENSION.equals(uri.fileExtension());
  }

  @Override
  public boolean isPlatformLibraryResource(String vsumRelativePath) {
    for (String segment : vsumRelativePath.split("/")) {
      if (PLATFORM_PACKAGE_ROOTS.contains(segment)) {
        return true;
      }
    }

    return false;
  }
}
