package tools.vitruv.dsls.reactions.migration.migration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.EcoreUtil.Copier;
import org.eclipse.emf.ecore.util.EcoreUtil.UsageCrossReferencer;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.framework.views.CommittableView;

final class SelectiveRepropagation {
  private static final List<String> UNASSIGNABLE_ID_MARKERS =
      List.of("trying to assign UUID for unknown element", "dangling object");

  private SelectiveRepropagation() {}

  static void execute(
      CommittableView committable,
      List<EObject> affectedElements,
      Predicate<EObject> restoreIncomingFor,
      AdapterRegistry adapters,
      Path vsumFolder,
      PhaseTimer timer) {
    List<ContainmentPosition> positions = capturePositions(affectedElements);
    List<IncomingReference> incomingReferences =
        captureIncomingReferences(affectedElements, adapters, vsumFolder);
    Copier copier = copyAffected(affectedElements);
    List<IncomingReference> crossCopyReferences =
        detachCrossCopyReferences(affectedElements, copier);
    ResourceSet resourceSet = affectedElements.getFirst().eResource().getResourceSet();
    Map<Resource, Integer> libraryRootCounts = libraryRootCounts(resourceSet, adapters, vsumFolder);
    timer.time(
        "delete-commit",
        () -> {
          clearIncomingReferences(incomingReferences);
          detachAffected(affectedElements);
          commitDeletions(committable);
          requireLibrariesIntact(libraryRootCounts);
        });
    timer.time(
        "reinsert-commit",
        () -> {
          reinsertCopies(positions, copier);
          restoreCrossCopyReferences(crossCopyReferences);
          restoreIncomingReferences(incomingReferences, copier, restoreIncomingFor);
          committable.commitChanges();
        });
  }

  private static void commitDeletions(CommittableView committable) {
    try {
      committable.commitChanges();
    } catch (RuntimeException e) {
      IllegalStateException unassignable = unassignableId(e);
      if (unassignable == null) {
        throw e;
      }

      throw new FallbackToFullMigration(
          "the delete-commit could not assign ids: " + unassignable.getMessage(), unassignable);
    }
  }

  private static Map<Resource, Integer> libraryRootCounts(
      ResourceSet resourceSet, AdapterRegistry adapters, Path vsumFolder) {
    Map<Resource, Integer> counts = new HashMap<>();
    for (Resource resource : List.copyOf(resourceSet.getResources())) {
      if (resource.getURI() != null && !adapters.isMigratedModel(resource.getURI(), vsumFolder)) {
        counts.put(resource, resource.getContents().size());
      }
    }

    return counts;
  }

  private static void requireLibrariesIntact(Map<Resource, Integer> before) {
    for (Map.Entry<Resource, Integer> entry : before.entrySet()) {
      int now = entry.getKey().getContents().size();
      if (now < entry.getValue()) {
        throw new IllegalStateException(
            "The delete-commit removed "
                + (entry.getValue() - now)
                + " root(s) from the library resource "
                + entry.getKey().getURI()
                + ". That commit is already persisted, so the migration cannot fall back to a"
                + " full run without re-deriving from the truncated sources; restore the VSUM"
                + " (--backup) and rerun with --mode full.");
      }
    }
  }

  private static IllegalStateException unassignableId(Throwable thrown) {
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Throwable cause = thrown; cause != null && seen.add(cause); cause = cause.getCause()) {
      if (cause instanceof IllegalStateException illegalState
          && isUnassignableIdMessage(illegalState.getMessage())) {
        return illegalState;
      }
    }

    return null;
  }

  private static boolean isUnassignableIdMessage(String message) {
    return message != null && UNASSIGNABLE_ID_MARKERS.stream().anyMatch(message::contains);
  }

  private static List<ContainmentPosition> capturePositions(List<EObject> affectedElements) {
    List<ContainmentPosition> positions = new ArrayList<>();
    for (EObject element : affectedElements) {
      EObject container = element.eContainer();
      EReference feature = element.eContainmentFeature();
      int index = feature.isMany() ? ((List<?>) container.eGet(feature)).indexOf(element) : -1;
      positions.add(new ContainmentPosition(element, container, feature, index));
    }

    return positions;
  }

  private static List<IncomingReference> captureIncomingReferences(
      List<EObject> affectedElements, AdapterRegistry adapters, Path vsumFolder) {
    Set<EObject> subtreeElements = subtreeElementsOf(affectedElements);
    ResourceSet resourceSet = affectedElements.getFirst().eResource().getResourceSet();
    List<IncomingReference> incomingReferences = new ArrayList<>();
    for (Resource resource : coveredResources(resourceSet, adapters, vsumFolder)) {
      UsageCrossReferencer.findAll(subtreeElements, resource)
          .forEach(
              (target, settings) -> {
                for (EStructuralFeature.Setting setting : settings) {
                  if (isRestorableIncomingReference(setting, subtreeElements)) {
                    incomingReferences.add(incomingReferenceOf(setting, target));
                  }
                }
              });
    }
    return incomingReferences;
  }

  private static List<Resource> coveredResources(
      ResourceSet resourceSet, AdapterRegistry adapters, Path vsumFolder) {
    List<Resource> covered = new ArrayList<>();
    for (Resource resource : List.copyOf(resourceSet.getResources())) {
      if (adapters.isMigratedModel(resource.getURI(), vsumFolder)) {
        covered.add(resource);
      }
    }
    return covered;
  }

  private static Set<EObject> subtreeElementsOf(List<EObject> affectedElements) {
    Set<EObject> subtreeElements = Collections.newSetFromMap(new IdentityHashMap<>());
    for (EObject element : affectedElements) {
      subtreeElements.add(element);
      element.eAllContents().forEachRemaining(subtreeElements::add);
    }

    return subtreeElements;
  }

  private static boolean isRestorableIncomingReference(
      EStructuralFeature.Setting setting, Set<EObject> subtreeElements) {
    return setting.getEStructuralFeature() instanceof EReference reference
        && !reference.isContainment()
        && !reference.isContainer()
        && reference.isChangeable()
        && !reference.isDerived()
        && !subtreeElements.contains(setting.getEObject());
  }

  private static IncomingReference incomingReferenceOf(
      EStructuralFeature.Setting setting, EObject target) {
    EObject owner = setting.getEObject();
    EReference feature = (EReference) setting.getEStructuralFeature();
    int index = feature.isMany() ? ((List<?>) owner.eGet(feature)).indexOf(target) : -1;
    return new IncomingReference(owner, feature, index, target);
  }

  private static Copier copyAffected(List<EObject> affectedElements) {
    Copier copier = new Copier();
    copier.copyAll(affectedElements);
    copier.copyReferences();
    return copier;
  }

  private static List<IncomingReference> detachCrossCopyReferences(
      List<EObject> affectedElements, Copier copier) {
    Map<EObject, Integer> subtreeOf = subtreeIndexOfCopies(affectedElements, copier);
    List<IncomingReference> crossReferences = new ArrayList<>();
    for (EObject owner : List.copyOf(subtreeOf.keySet())) {
      collectCrossReferences(owner, subtreeOf, crossReferences);
    }

    detachAll(crossReferences);
    return crossReferences;
  }

  private static Map<EObject, Integer> subtreeIndexOfCopies(
      List<EObject> affectedElements, Copier copier) {
    Map<EObject, Integer> subtreeOf = new HashMap<>();
    for (int i = 0; i < affectedElements.size(); i++) {
      EObject copyRoot = copier.get(affectedElements.get(i));
      subtreeOf.put(copyRoot, i);
      int subtree = i;
      copyRoot.eAllContents().forEachRemaining(child -> subtreeOf.put(child, subtree));
    }

    return subtreeOf;
  }

  private static void collectCrossReferences(
      EObject owner, Map<EObject, Integer> subtreeOf, List<IncomingReference> crossReferences) {
    for (EReference reference : owner.eClass().getEAllReferences()) {
      if (reference.isContainment()
          || reference.isContainer()
          || !reference.isChangeable()
          || reference.isDerived()) {
        continue;
      }

      if (reference.isMany()) {
        List<EObject> values = referenceList(owner, reference);
        for (int index = values.size() - 1; index >= 0; index--) {
          addIfCrossCopy(crossReferences, subtreeOf, owner, reference, index, values.get(index));
        }
      } else if (owner.eGet(reference) instanceof EObject target) {
        addIfCrossCopy(crossReferences, subtreeOf, owner, reference, -1, target);
      }
    }
  }

  private static void addIfCrossCopy(
      List<IncomingReference> crossReferences,
      Map<EObject, Integer> subtreeOf,
      EObject owner,
      EReference feature,
      int index,
      EObject target) {
    int ownerSubtree = subtreeOf.get(owner);
    Integer targetSubtree = subtreeOf.get(target);
    if (targetSubtree != null && targetSubtree != ownerSubtree) {
      crossReferences.add(new IncomingReference(owner, feature, index, target));
    }
  }

  private static void detachAll(List<IncomingReference> references) {
    for (IncomingReference reference : references) {
      if (reference.feature().isMany()) {
        referenceList(reference.owner(), reference.feature()).remove(reference.target());
      } else if (reference.owner().eGet(reference.feature()) != null) {
        reference.owner().eSet(reference.feature(), null);
      }
    }
  }

  private static void restoreCrossCopyReferences(List<IncomingReference> crossCopyReferences) {
    for (IncomingReference reference : byAscendingIndex(crossCopyReferences)) {
      putBack(reference.owner(), reference.feature(), reference.index(), reference.target());
    }
  }

  private static List<IncomingReference> byAscendingIndex(List<IncomingReference> references) {
    List<IncomingReference> sorted = new ArrayList<>(references);
    sorted.sort(Comparator.comparingInt(IncomingReference::index));
    return sorted;
  }

  private static void putBack(EObject owner, EReference feature, int index, EObject value) {
    if (!feature.isMany()) {
      owner.eSet(feature, value);
      return;
    }

    List<EObject> values = referenceList(owner, feature);
    if (!values.contains(value)) {
      values.add(Math.min(index, values.size()), value);
    }
  }

  private static void clearIncomingReferences(List<IncomingReference> incomingReferences) {
    for (IncomingReference incoming : incomingReferences) {
      if (incoming.feature().isMany()) {
        referenceList(incoming.owner(), incoming.feature()).remove(incoming.target());
      } else {
        incoming.owner().eSet(incoming.feature(), null);
      }
    }
  }

  private static void detachAffected(List<EObject> affectedElements) {
    affectedElements.forEach(EcoreUtil::remove);
  }

  private static void reinsertCopies(List<ContainmentPosition> positions, Copier copier) {
    for (ContainmentPosition position : positions) {
      EObject copy = copier.get(position.element());
      if (position.feature().isMany()) {
        List<EObject> siblings = referenceList(position.container(), position.feature());
        siblings.add(Math.min(position.index(), siblings.size()), copy);
      } else {
        position.container().eSet(position.feature(), copy);
      }
    }
  }

  private static void restoreIncomingReferences(
      List<IncomingReference> incomingReferences,
      Copier copier,
      Predicate<EObject> restoreIncomingFor) {
    for (IncomingReference incoming : byAscendingIndex(incomingReferences)) {
      EObject reinsertedTarget = copier.get(incoming.target());
      if (reinsertedTarget == null || !restoreIncomingFor.test(incoming.owner())) {
        continue;
      }

      putBack(incoming.owner(), incoming.feature(), incoming.index(), reinsertedTarget);
    }
  }

  @SuppressWarnings("unchecked")
  private static List<EObject> referenceList(EObject owner, EReference feature) {
    return (List<EObject>) owner.eGet(feature);
  }

  private record ContainmentPosition(
      EObject element, EObject container, EReference feature, int index) {}

  private record IncomingReference(EObject owner, EReference feature, int index, EObject target) {}
}
