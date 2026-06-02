package tools.vitruv.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.UMLFactory;
import org.emftext.language.java.JavaClasspath;
import org.emftext.language.java.classifiers.Class;
import org.emftext.language.java.classifiers.ConcreteClassifier;
import org.emftext.language.java.containers.CompilationUnit;
import org.emftext.language.java.containers.ContainersFactory;
import org.emftext.language.java.containers.JavaRoot;
import org.emftext.language.java.containers.Package;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.applications.umljava.JavaToUmlChangePropagationSpecification;
import tools.vitruv.applications.umljava.UmlToJavaChangePropagationSpecification;
import tools.vitruv.applications.util.temporary.java.JavaContainerAndClassifierUtil;
import tools.vitruv.applications.util.temporary.java.JavaMemberAndParameterUtil;
import tools.vitruv.applications.util.temporary.java.JavaModificationUtil;
import tools.vitruv.applications.util.temporary.java.JavaPersistenceHelper;
import tools.vitruv.applications.util.temporary.java.JavaSetup;
import tools.vitruv.applications.util.temporary.java.JavaStandardType;
import tools.vitruv.applications.util.temporary.java.JavaVisibility;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.testutils.integration.TestViewFactory;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.ViewProvider;
import tools.vitruv.framework.views.ViewSelector;
import tools.vitruv.framework.views.ViewType;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

@ExtendWith(RegisterMetamodelsInStandalone.class)
class MigrationRunnerIntegrationTest {
  private static final List<ChangePropagationSpecification> SPECIFICATIONS =
      List.of(
          new UmlToJavaChangePropagationSpecification(),
          new JavaToUmlChangePropagationSpecification());

  private static final String JAVA_NS_URI_PREFIX = "http://www.emftext.org/java";
  private static final String UML_NS_URI = "http://www.eclipse.org/uml2/5.0.0/UML";
  private static final String CLASS_NAME = "Greeter";

  @TempDir Path tempDir;

  private Path sourceFolder;
  private InternalVirtualModel sourceVsum;

  @BeforeAll
  static void initJavaFactories() {
    JavaSetup.prepareFactories();
  }

  private static Set<String> umlClassAttributeNames(Model model, String className) {
    return model.getPackagedElements().stream()
        .filter(org.eclipse.uml2.uml.Class.class::isInstance)
        .map(org.eclipse.uml2.uml.Class.class::cast)
        .filter(clazz -> className.equals(clazz.getName()))
        .flatMap(clazz -> clazz.getOwnedAttributes().stream())
        .map(org.eclipse.uml2.uml.NamedElement::getName)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static Model firstUmlModel(List<Resource> resources) {
    return resources.stream()
        .flatMap(resource -> resource.getContents().stream())
        .filter(Model.class::isInstance)
        .map(Model.class::cast)
        .findFirst()
        .orElseThrow(() -> new AssertionError("no UML Model among " + resources));
  }

  private static java.util.Map<String, String> umlClassesByNameAndVisibility(Model model) {
    java.util.Map<String, String> result = new java.util.TreeMap<>();
    model.getPackagedElements().stream()
        .filter(org.eclipse.uml2.uml.Class.class::isInstance)
        .map(org.eclipse.uml2.uml.Class.class::cast)
        .forEach(clazz -> result.put(clazz.getName(), clazz.getVisibility().getLiteral()));
    return result;
  }

  private static List<Resource> loadModelResources(Path folder, String extension)
      throws IOException {
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet.getLoadOptions().put("DISABLE_LAYOUT_INFORMATION_RECORDING", Boolean.TRUE);
    List<Resource> resources = new ArrayList<>();
    try (var walk = Files.walk(folder)) {
      walk.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(extension))
          .forEach(
              path ->
                  resources.add(
                      resourceSet.getResource(
                          URI.createFileURI(path.toAbsolutePath().toString()), true)));
    }
    return resources;
  }

  private static Model loadUmlModel(URI uri) {
    ResourceSet resourceSet = new ResourceSetImpl();
    Resource resource = resourceSet.getResource(uri, true);
    return resource.getContents().stream()
        .filter(Model.class::isInstance)
        .map(Model.class::cast)
        .findFirst()
        .orElseThrow(() -> new AssertionError("no UML Model in " + uri));
  }

  private static void assertAllPersistedModelsValid(Path folder) throws IOException {
    List<Path> modelFiles;
    try (var walk = Files.walk(folder)) {
      modelFiles =
          walk.filter(Files::isRegularFile)
              .filter(
                  path -> {
                    String name = path.getFileName().toString();
                    return name.endsWith(".uml") || name.endsWith(".java");
                  })
              .toList();
    }

    assertThat(modelFiles).as("the migration should persist model files").isNotEmpty();
    for (Path file : modelFiles) {
      ResourceSet resourceSet = new ResourceSetImpl();
      Resource resource =
          resourceSet.getResource(URI.createFileURI(file.toAbsolutePath().toString()), true);
      assertThat(resource.getErrors()).as("no load errors in %s", file).isEmpty();
      assertThat(resource.getContents()).as("%s should contain a model root", file).isNotEmpty();
    }
  }

  private static List<EObject> readRoots(ViewProvider provider) {
    View view = viewOfAll(provider, "read-all");
    try {
      return List.copyOf(view.getRootObjects());
    } finally {
      try {
        view.close();
      } catch (Exception ignored) {
        // best-effort close in test scope
      }
    }
  }

  private static Set<String> nsUrisOf(List<EObject> roots) {
    return roots.stream()
        .map(root -> root.eClass().getEPackage().getNsURI())
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static View viewOfAll(ViewProvider provider, String name) {
    ViewType<? extends ViewSelector> viewType = ViewTypeFactory.createIdentityMappingViewType(name);
    ViewSelector selector = provider.createSelector(viewType);
    selector.getSelectableElements().forEach(element -> selector.setSelected(element, true));
    return selector.createView();
  }

  @BeforeEach
  void buildSeedVsum() throws IOException {
    JavaSetup.resetClasspathAndRegisterStandardLibrary();

    sourceFolder = tempDir.resolve("source-vsum");
    Files.createDirectories(sourceFolder);

    TestUserInteraction userInteraction = new TestUserInteraction();
    userInteraction.onTextInput(description -> true).always().respondWith("model");
    userInteraction
        .onMultipleChoiceSingleSelection(description -> true)
        .always()
        .respondWithChoiceAt(0);
    userInteraction.onConfirmation(description -> true).always().respondWith(true);

    sourceVsum =
        new VirtualModelBuilder()
            .withStorageFolder(sourceFolder)
            .withUserInteractorForResultProvider(
                new TestUserInteraction.ResultProvider(userInteraction))
            .withChangePropagationSpecifications(SPECIFICATIONS)
            .buildAndInitialize();
  }

  @AfterEach
  void disposeSeedVsum() {
    if (sourceVsum != null) {
      sourceVsum.dispose();
      sourceVsum = null;
    }
  }

  @Test
  void seedingJavaClassPropagatesToUmlInRealVsum() throws Exception {
    seedJavaClass(sourceVsum, CLASS_NAME);

    Set<String> seededNsUris = nsUrisOf(readRoots(sourceVsum));
    assertThat(seededNsUris)
        .as("the UML model should be created by the Java -> UML reactions")
        .contains(UML_NS_URI);
    assertThat(seededNsUris)
        .as("the seeded Java (JaMoPP) root should be present")
        .anyMatch(nsUri -> nsUri.startsWith(JAVA_NS_URI_PREFIX));

    sourceVsum.dispose();
    sourceVsum = null;
    PropagationGraph graph = PropagationGraph.fromSpecifications(SPECIFICATIONS);
    assertThat(graph.nodeContaining(UML_NS_URI)).isPresent();
    assertThat(graph.nodeContaining(JAVA_NS_URI_PREFIX + "/containers"))
        .as("a Java subpackage model must resolve to the Java node (subpackage ownership)")
        .isPresent();
  }

  @Test
  void propagatesUmlBaseFromDiskToJavaAndAllModelsAreValid() throws Exception {
    Path baseFolder = tempDir.resolve("base");
    URI baseUmlUri = createUmlBaseOnDisk(baseFolder);
    assertThat(Path.of(baseUmlUri.toFileString()))
        .as("the UML base model should be saved to disk")
        .exists();

    Model detachedBase = EcoreUtil.copy(loadUmlModel(baseUmlUri));
    URI umlUriInVsum =
        URI.createFileURI(sourceFolder.resolve("model.uml").toAbsolutePath().toString());
    TestViewFactory viewFactory = new TestViewFactory(sourceVsum);
    View umlView = viewFactory.createViewOfElements("UML", Set.of(Model.class));
    viewFactory.changeViewRecordingChanges(
        umlView, committable -> committable.registerRoot(detachedBase, umlUriInVsum));

    Set<String> nsUris = nsUrisOf(readRoots(sourceVsum));
    assertThat(nsUris).as("the UML base must still be present").contains(UML_NS_URI);
    assertThat(nsUris)
        .as("the reactions must have generated a Java (JaMoPP) model")
        .anyMatch(nsUri -> nsUri.startsWith(JAVA_NS_URI_PREFIX));

    sourceVsum.dispose();
    sourceVsum = null;
    assertAllPersistedModelsValid(sourceFolder);
  }

  @Test
  void reInsertingPersistedJavaModelPropagatesToUml() throws Exception {
    seedJavaClass(sourceVsum, CLASS_NAME);
    sourceVsum.dispose();
    sourceVsum = null;

    Path targetFolder = tempDir.resolve("target-vsum");
    Set<String> nsUris = reInsertJavaAndPropagate(targetFolder);

    assertThat(nsUris)
        .as("the re-inserted Java model must be present")
        .anyMatch(nsUri -> nsUri.startsWith(JAVA_NS_URI_PREFIX));
    assertThat(nsUris)
        .as("the Java -> UML reactions must regenerate the UML model")
        .contains(UML_NS_URI);
    assertAllPersistedModelsValid(targetFolder);
  }

  @Test
  void reInsertingPersistedJavaInterfacePropagatesToUml() throws Exception {
    seedJavaClassifiers(
        sourceVsum, JavaContainerAndClassifierUtil.createJavaInterface("Greetable", List.of()));
    sourceVsum.dispose();
    sourceVsum = null;

    Path targetFolder = tempDir.resolve("target-interface");
    Set<String> nsUris = reInsertJavaAndPropagate(targetFolder);

    assertThat(nsUris).anyMatch(nsUri -> nsUri.startsWith(JAVA_NS_URI_PREFIX));
    assertThat(nsUris)
        .as("the Java -> UML reactions must regenerate UML for an interface too")
        .contains(UML_NS_URI);
    assertAllPersistedModelsValid(targetFolder);
  }

  @Test
  void reInsertingMultiplePersistedJavaClassesPropagatesToUml() throws Exception {
    seedJavaClassifiers(
        sourceVsum,
        JavaContainerAndClassifierUtil.createJavaClass(
            "Alpha", JavaVisibility.PUBLIC, false, false),
        JavaContainerAndClassifierUtil.createJavaClass("Beta", JavaVisibility.PUBLIC, false, false),
        JavaContainerAndClassifierUtil.createJavaClass(
            "Gamma", JavaVisibility.PUBLIC, false, false));
    sourceVsum.dispose();
    sourceVsum = null;

    Path targetFolder = tempDir.resolve("target-multi");
    Set<String> nsUris = reInsertJavaAndPropagate(targetFolder);

    assertThat(nsUris).contains(UML_NS_URI);
    Model rederived = firstUmlModel(loadModelResources(targetFolder, ".uml"));
    assertThat(umlClassesByNameAndVisibility(rederived).keySet())
        .as("all three re-inserted Java classes must appear in the regenerated UML")
        .contains("Alpha", "Beta", "Gamma");
    assertAllPersistedModelsValid(targetFolder);
  }

  @Test
  void reInsertingPersistedJavaClassWithFieldPropagatesToUml() throws Exception {
    Class counter =
        JavaContainerAndClassifierUtil.createJavaClass(
            "Counter", JavaVisibility.PUBLIC, false, false);
    counter
        .getMembers()
        .add(
            JavaMemberAndParameterUtil.createJavaAttribute(
                "count",
                JavaStandardType.createJavaPrimitiveType(JavaStandardType.INT),
                JavaVisibility.PRIVATE,
                false,
                false));
    seedJavaClassifiers(sourceVsum, counter);
    sourceVsum.dispose();
    sourceVsum = null;

    Path targetFolder = tempDir.resolve("target-field");
    Set<String> nsUris = reInsertJavaAndPropagate(targetFolder);

    assertThat(nsUris).contains(UML_NS_URI);
    Model rederived = firstUmlModel(loadModelResources(targetFolder, ".uml"));
    assertThat(umlClassesByNameAndVisibility(rederived).keySet()).contains("Counter");
    assertThat(umlClassAttributeNames(rederived, "Counter"))
        .as("the Java field must propagate to a UML property")
        .contains("count");
    assertAllPersistedModelsValid(targetFolder);
  }

  @Disabled(
      "Fields typed by standard-library classes need (1) the standard library treated as a read-only"
          + " classpath - solvable outside Vitruv-Change and proven to remove the cache:/0 abort -"
          + " and (2) umljava reactions that can map JDK types, which they cannot (Static classifiers"
          + " not supported -> NullPointerException). The remaining blocker is the reactions, not the"
          + " framework. See javadoc.")
  @Test
  void reInsertingPersistedJavaClassWithStringFieldPropagatesToUml() throws Exception {
    ConcreteClassifier stringClass =
        (ConcreteClassifier) JavaClasspath.get().getClassifier("java.lang.String");
    Class holder =
        JavaContainerAndClassifierUtil.createJavaClass(
            "Holder", JavaVisibility.PUBLIC, false, false);
    holder
        .getMembers()
        .add(
            JavaMemberAndParameterUtil.createJavaAttribute(
                "label",
                JavaModificationUtil.createNamespaceClassifierReference(stringClass),
                JavaVisibility.PRIVATE,
                false,
                false));
    seedJavaClassifiers(sourceVsum, holder);
    sourceVsum.dispose();
    sourceVsum = null;

    Path targetFolder = tempDir.resolve("target-stringfield");
    Set<String> nsUris = reInsertJavaAndPropagate(targetFolder);

    assertThat(nsUris).contains(UML_NS_URI);
    Model rederived = firstUmlModel(loadModelResources(targetFolder, ".uml"));
    assertThat(umlClassAttributeNames(rederived, "Holder"))
        .as("the String-typed Java field must propagate to a UML property")
        .contains("label");
    assertAllPersistedModelsValid(targetFolder);
  }

  @Test
  void reDerivedUmlHasSameContentAsOriginalForUnchangedReactions() throws Exception {
    seedJavaClass(sourceVsum, CLASS_NAME);
    sourceVsum.dispose();
    sourceVsum = null;

    Path targetFolder = tempDir.resolve("target-fidelity");
    reInsertJavaAndPropagate(targetFolder);

    Model original = firstUmlModel(loadModelResources(sourceFolder, ".uml"));
    Model rederived = firstUmlModel(loadModelResources(targetFolder, ".uml"));
    assertThat(rederived.getName())
        .as("re-derived UML model name must match the original")
        .isEqualTo(original.getName());
    assertThat(umlClassesByNameAndVisibility(rederived))
        .as("re-derived UML must contain the same classes with the same visibility")
        .isEqualTo(umlClassesByNameAndVisibility(original));
  }

  private Set<String> reInsertJavaAndPropagate(Path targetFolder) throws Exception {
    Files.createDirectories(targetFolder);
    TestUserInteraction interaction = new TestUserInteraction();
    interaction.onTextInput(description -> true).always().respondWith("model");
    interaction
        .onMultipleChoiceSingleSelection(description -> true)
        .always()
        .respondWithChoiceAt(0);
    interaction.onConfirmation(description -> true).always().respondWith(true);
    InternalVirtualModel targetVsum =
        new VirtualModelBuilder()
            .withStorageFolder(targetFolder)
            .withUserInteractorForResultProvider(
                new TestUserInteraction.ResultProvider(interaction))
            .withChangePropagationSpecifications(SPECIFICATIONS)
            .buildAndInitialize();
    try {
      List<EObject> originals = new ArrayList<>();
      for (Resource javaResource : loadModelResources(sourceFolder, ".java")) {
        originals.addAll(javaResource.getContents());
      }
      List<EObject> copies = new ArrayList<>(EcoreUtil.copyAll(originals));
      TestViewFactory viewFactory = new TestViewFactory(targetVsum);
      View javaView =
          viewFactory.createViewOfElements("Java", Set.of(Package.class, CompilationUnit.class));
      viewFactory.changeViewRecordingChanges(
          javaView,
          committable ->
              copies.forEach(
                  copy -> {
                    JavaRoot root = (JavaRoot) copy;
                    URI uri =
                        URI.createFileURI(
                            targetFolder
                                .resolve(JavaPersistenceHelper.buildJavaFilePath(root))
                                .toAbsolutePath()
                                .toString());
                    committable.registerRoot(root, uri);
                  }));
      return nsUrisOf(readRoots(targetVsum));
    } finally {
      targetVsum.dispose();
    }
  }

  private URI createUmlBaseOnDisk(Path baseFolder) throws Exception {
    Files.createDirectories(baseFolder);
    TestUserInteraction noInteraction = new TestUserInteraction();
    InternalVirtualModel baseVsum =
        new VirtualModelBuilder()
            .withStorageFolder(baseFolder)
            .withUserInteractorForResultProvider(
                new TestUserInteraction.ResultProvider(noInteraction))
            .withChangePropagationSpecifications(List.of())
            .buildAndInitialize();
    URI umlUri = URI.createFileURI(baseFolder.resolve("model.uml").toAbsolutePath().toString());
    try {
      TestViewFactory viewFactory = new TestViewFactory(baseVsum);
      View umlView = viewFactory.createViewOfElements("UML base", Set.of(Model.class));
      viewFactory.changeViewRecordingChanges(
          umlView,
          committable -> {
            Model model = UMLFactory.eINSTANCE.createModel();
            model.setName("model");
            model.createOwnedClass(MigrationRunnerIntegrationTest.CLASS_NAME, false);
            committable.registerRoot(model, umlUri);
          });
    } finally {
      baseVsum.dispose();
    }

    return umlUri;
  }

  private void seedJavaClass(ViewProvider provider, String className) throws Exception {
    seedJavaClassifiers(
        provider,
        JavaContainerAndClassifierUtil.createJavaClass(
            className, JavaVisibility.PUBLIC, false, false));
  }

  private void seedJavaClassifiers(ViewProvider provider, ConcreteClassifier... classifiers)
      throws Exception {
    TestViewFactory viewFactory = new TestViewFactory(provider);
    View javaView =
        viewFactory.createViewOfElements(
            "Java packages and classes", Set.of(Package.class, CompilationUnit.class));
    viewFactory.changeViewRecordingChanges(
        javaView,
        committable -> {
          for (ConcreteClassifier classifier : classifiers) {
            CompilationUnit compilationUnit = ContainersFactory.eINSTANCE.createCompilationUnit();
            JavaContainerAndClassifierUtil.updateCompilationUnitName(
                compilationUnit, classifier.getName());
            compilationUnit.getClassifiers().add(classifier);
            URI uri =
                URI.createFileURI(
                    sourceFolder
                        .resolve(JavaPersistenceHelper.buildJavaFilePath(compilationUnit))
                        .toString());
            committable.registerRoot(compilationUnit, uri);
          }
        });
  }
}
