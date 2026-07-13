package tools.vitruv.dsls.reactions.migration.adapter;

import java.util.List;
import java.util.Map;
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
  private static final String JAMOPP_NS_PREFIX = "http://www.emftext.org/java";
  private static final String CLASSPATH_CACHE_SCHEME = "cache";

  @SuppressWarnings("unchecked")
  private static void rebindClasspathReferences(EObject holder, JavaClasspath liveClasspath) {
    for (EReference feature : holder.eClass().getEAllReferences()) {
      if (feature.isContainment() || feature.isContainer() || !feature.isChangeable()) {
        continue;
      }

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
  public void prepareForSnapshot(EObject root) {
    if (root instanceof Package javaPackage) {
      javaPackage.getCompilationUnits().clear();
    }
  }

  @Override
  public void prepareForReplayInto(ResourceSet liveResourceSet, Resource detachedResource) {
    JavaClasspath liveClasspath = JavaClasspath.get(liveResourceSet);
    detachedResource
        .getAllContents()
        .forEachRemaining(object -> rebindClasspathReferences(object, liveClasspath));
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
  public boolean requiresChangeRecording() {
    return true;
  }

  @Override
  public boolean isPlatformLibraryResource(URI uri) {
    String path = uri.toString();
    return path.contains("/java/")
        || path.contains("/javax/")
        || path.contains("/sun/")
        || path.contains("/jdk/");
  }
}
