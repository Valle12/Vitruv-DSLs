package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.compare.utils.UseIdentifiers;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Enumeration;
import org.eclipse.uml2.uml.PackageableElement;
import org.eclipse.uml2.uml.UMLFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.views.changederivation.DefaultStateBasedChangeResolutionStrategy;

@ExtendWith(RegisterMetamodelsInStandalone.class)
class ModelStateDiffTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();

  private static final Set<String> CLASSES_REALIZED_AS_ENUMS =
      Set.of("Media", "Book", "LibraryCard", "Member");

  @BeforeAll
  static void prepareStandalone() {
    Vsums.prepareStandalone(ADAPTERS);
  }

  private static Resource libraryModel() throws Exception {
    URL fixture =
        Objects.requireNonNull(
            ModelStateDiffTest.class.getResource("/persisted-library-vsum/model/library.uml"));
    Path file = Path.of(fixture.toURI());
    return ADAPTERS.load(ADAPTERS.newResourceSet(), URI.createFileURI(file.toString()));
  }

  private static void giveFreshIdentifiers(Resource resource) {
    XMLResource xml = (XMLResource) resource;
    List<EObject> elements = new ArrayList<>();
    resource.getAllContents().forEachRemaining(elements::add);
    elements.forEach(element -> xml.setID(element, EcoreUtil.generateUUID()));
  }

  private static Classifier firstClassifierOf(Resource resource) {
    var contents = resource.getAllContents();
    while (contents.hasNext()) {
      if (contents.next() instanceof Classifier classifier) {
        return classifier;
      }
    }

    throw new AssertionError("the library model holds classifiers");
  }

  private static List<org.eclipse.uml2.uml.Class> classesRealizedAsEnums(Resource resource) {
    List<org.eclipse.uml2.uml.Class> classes = new ArrayList<>();
    resource
        .getAllContents()
        .forEachRemaining(
            element -> {
              if (element instanceof org.eclipse.uml2.uml.Class clazz
                  && CLASSES_REALIZED_AS_ENUMS.contains(clazz.getName())) {
                classes.add(clazz);
              }
            });
    assertEquals(CLASSES_REALIZED_AS_ENUMS.size(), classes.size(), "all four classes exist");
    return classes;
  }

  private static void realizeClassesAsEnums(Resource resource) {
    for (org.eclipse.uml2.uml.Class clazz : classesRealizedAsEnums(resource)) {
      clazz.getInterfaceRealizations().clear();
      clazz.getGeneralizations().clear();

      Enumeration enumeration = UMLFactory.eINSTANCE.createEnumeration();
      enumeration.setName(clazz.getName());
      enumeration.setVisibility(clazz.getVisibility());
      enumeration.getOwnedAttributes().addAll(List.copyOf(clazz.getOwnedAttributes()));
      enumeration.getOwnedOperations().addAll(List.copyOf(clazz.getOwnedOperations()));

      Collection<EStructuralFeature.Setting> uses =
          EcoreUtil.UsageCrossReferencer.find(clazz, resource);
      for (EStructuralFeature.Setting use : List.copyOf(uses)) {
        EStructuralFeature feature = use.getEStructuralFeature();
        boolean writable =
            !feature.isDerived()
                && !(feature instanceof EReference reference && reference.isContainment());
        if (writable) {
          EcoreUtil.replace(use, clazz, enumeration);
        }
      }

      List<PackageableElement> siblings = clazz.getPackage().getPackagedElements();
      siblings.set(siblings.indexOf(clazz), enumeration);
    }
  }

  private static long frameworkCount(Resource newState, Resource oldState) {
    return new DefaultStateBasedChangeResolutionStrategy(UseIdentifiers.NEVER)
        .getChangeSequenceBetween(newState, oldState)
        .getEChanges()
        .size();
  }

  @Test
  @DisplayName("the same model with fresh identifiers counts as unchanged")
  void test1() throws Exception {
    Resource current = libraryModel();
    Resource proposed = libraryModel();
    giveFreshIdentifiers(proposed);

    assertEquals(0, new ModelStateDiff().changeCount(List.of(current), List.of(proposed)));
  }

  @Test
  @DisplayName("a renamed classifier counts as a change")
  void test2() throws Exception {
    Resource current = libraryModel();
    Resource proposed = libraryModel();
    giveFreshIdentifiers(proposed);
    firstClassifierOf(proposed).setName("Renamed");

    assertTrue(new ModelStateDiff().changeCount(List.of(current), List.of(proposed)) > 0);
  }

  @Test
  @DisplayName(
      "counting a re-derivation that turned classes into enumerations does not need the identifier"
          + " round trip")
  void test3() throws Exception {
    Resource current = libraryModel();
    Resource proposed = libraryModel();
    realizeClassesAsEnums(proposed);
    giveFreshIdentifiers(proposed);

    ModelStateDiff diff = new ModelStateDiff();
    long count = diff.changeCount(List.of(current), List.of(proposed));

    assertTrue(count > 0, "four classes became enumerations: " + count);
    assertEquals(diff.changesBetween(proposed, current).size(), count);
  }

  @Test
  @DisplayName("the count is the one the framework's change derivation takes where it succeeds")
  void test4() throws Exception {
    Resource current = libraryModel();
    Resource proposed = libraryModel();
    giveFreshIdentifiers(proposed);
    firstClassifierOf(proposed).setName("Renamed");

    assertEquals(
        frameworkCount(proposed, current),
        new ModelStateDiff().changeCount(List.of(current), List.of(proposed)));
  }

  @Test
  @DisplayName("a created and a deleted file count like the framework's change derivation")
  void test5() throws Exception {
    Resource model = libraryModel();
    DefaultStateBasedChangeResolutionStrategy framework =
        new DefaultStateBasedChangeResolutionStrategy(UseIdentifiers.NEVER);

    assertEquals(
        framework.getChangeSequenceForDeleted(model).getEChanges().size(),
        new ModelStateDiff().changeCount(List.of(model), List.of()),
        "deleted");
    assertEquals(
        framework.getChangeSequenceForCreated(model).getEChanges().size(),
        new ModelStateDiff().changeCount(List.of(), List.of(model)),
        "created");
  }
}
