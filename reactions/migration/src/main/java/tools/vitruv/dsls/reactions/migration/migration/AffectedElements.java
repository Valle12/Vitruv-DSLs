package tools.vitruv.dsls.reactions.migration.migration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import tools.vitruv.change.atomic.EChange;
import tools.vitruv.change.propagation.ConsistencyRuleTriggerMatcher;

final class AffectedElements {
  private AffectedElements() {}

  static List<EObject> find(
      List<EObject> sourceRoots, Collection<ConsistencyRuleTriggerMatcher> matchers) {
    return find(sourceRoots, matchers, element -> false);
  }

  static List<EObject> find(
      List<EObject> sourceRoots,
      Collection<ConsistencyRuleTriggerMatcher> matchers,
      Predicate<EObject> excluded) {
    List<EObject> matched = new ArrayList<>();
    for (EObject root : sourceRoots) {
      addIfMatched(matched, root, matchers, excluded);
      Iterator<EObject> it = root.eAllContents();
      while (it.hasNext()) {
        addIfMatched(matched, it.next(), matchers, excluded);
      }
    }

    return withoutNestedMatches(matched);
  }

  private static void addIfMatched(
      List<EObject> matched,
      EObject element,
      Collection<ConsistencyRuleTriggerMatcher> matchers,
      Predicate<EObject> excluded) {
    if (excluded.test(element)) {
      return;
    }

    for (EChange<EObject> probe : ChangeProbes.forElement(element)) {
      for (ConsistencyRuleTriggerMatcher matcher : matchers) {
        if (matcher.matches(probe)) {
          matched.add(element);
          return;
        }
      }
    }
  }

  static List<EObject> withSourceReferrers(
      List<EObject> affected, List<EObject> sourceRoots, Predicate<EObject> excluded) {
    if (affected.isEmpty()) {
      return affected;
    }

    Set<EObject> subtreeElements = subtreeElementsOf(affected);
    List<EObject> extended = new ArrayList<>(affected);
    for (List<EObject> found = referrersOf(subtreeElements, sourceRoots, excluded);
        !found.isEmpty();
        found = referrersOf(subtreeElements, sourceRoots, excluded)) {
      extended.addAll(found);
      found.forEach(
          element -> {
            subtreeElements.add(element);
            element.eAllContents().forEachRemaining(subtreeElements::add);
          });
    }

    return withoutNestedMatches(extended);
  }

  private static List<EObject> referrersOf(
      Set<EObject> subtreeElements, List<EObject> sourceRoots, Predicate<EObject> excluded) {
    List<EObject> referrers = new ArrayList<>();
    for (EObject root : sourceRoots) {
      Iterator<EObject> it = root.eAllContents();
      while (it.hasNext()) {
        EObject element = it.next();
        if (!subtreeElements.contains(element)
            && !excluded.test(element)
            && element.eContainer() != null
            && pointsInto(element, subtreeElements)) {
          referrers.add(element);
        }
      }
    }

    return referrers;
  }

  private static boolean pointsInto(EObject element, Set<EObject> subtreeElements) {
    for (EReference reference : element.eClass().getEAllReferences()) {
      if (reference.isContainment()
          || reference.isContainer()
          || reference.isDerived()
          || !reference.isChangeable()
          || !element.eIsSet(reference)) {
        continue;
      }

      Object value = element.eGet(reference);
      if (reference.isMany()
          ? ((List<?>) value).stream().anyMatch(each -> isSubtreeElement(subtreeElements, each))
          : isSubtreeElement(subtreeElements, value)) {
        return true;
      }
    }

    return false;
  }

  private static boolean isSubtreeElement(Set<EObject> subtreeElements, Object value) {
    return value instanceof EObject referenced && subtreeElements.contains(referenced);
  }

  private static Set<EObject> subtreeElementsOf(List<EObject> elements) {
    Set<EObject> subtreeElements = Collections.newSetFromMap(new IdentityHashMap<>());
    for (EObject element : elements) {
      subtreeElements.add(element);
      element.eAllContents().forEachRemaining(subtreeElements::add);
    }

    return subtreeElements;
  }

  private static List<EObject> withoutNestedMatches(List<EObject> matched) {
    Set<EObject> all = Collections.newSetFromMap(new IdentityHashMap<>());
    all.addAll(matched);
    return matched.stream().filter(element -> !hasMatchedAncestor(all, element)).toList();
  }

  private static boolean hasMatchedAncestor(Set<EObject> matched, EObject element) {
    for (EObject container = element.eContainer();
        container != null;
        container = container.eContainer()) {
      if (matched.contains(container)) {
        return true;
      }
    }

    return false;
  }
}
