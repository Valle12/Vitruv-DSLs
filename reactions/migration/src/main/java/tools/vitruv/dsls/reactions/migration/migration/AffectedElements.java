package tools.vitruv.dsls.reactions.migration.migration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import tools.vitruv.change.atomic.EChange;
import tools.vitruv.change.propagation.ConsistencyRuleId;
import tools.vitruv.change.propagation.ConsistencyRuleTriggerMatcher;

final class AffectedElements {
  @SuppressWarnings("unused")
  private AffectedElements() {}

  static List<EObject> find(
      List<EObject> sourceRoots, Collection<ConsistencyRuleTriggerMatcher> matchers) {
    return find(sourceRoots, matchers, element -> false);
  }

  static List<EObject> find(
      List<EObject> sourceRoots,
      Collection<ConsistencyRuleTriggerMatcher> matchers,
      Predicate<EObject> excluded) {
    List<DirtyMatcher> unnamed = new ArrayList<>();
    for (ConsistencyRuleTriggerMatcher matcher : matchers) {
      unnamed.add(new DirtyMatcher(ConsistencyRuleId.of("matcher::" + unnamed.size()), matcher));
    }

    return matched(sourceRoots, unnamed, excluded).elements().stream()
        .map(Selected::element)
        .toList();
  }

  static Selection select(
      List<EObject> sourceRoots, List<DirtyMatcher> matchers, Predicate<EObject> excluded) {
    Selection matched = matched(sourceRoots, matchers, excluded);
    return new Selection(
        withSourceReferrers(matched.elements(), sourceRoots, excluded),
        matched.matchesPerRule(),
        matched.probedElements());
  }

  private static Selection matched(
      List<EObject> sourceRoots, List<DirtyMatcher> matchers, Predicate<EObject> excluded) {
    List<Selected> matched = new ArrayList<>();
    Map<ConsistencyRuleId, Integer> matchesPerRule = new LinkedHashMap<>();
    matchers.forEach(matcher -> matchesPerRule.put(matcher.id(), 0));
    int probed = 0;
    for (EObject root : sourceRoots) {
      probed++;
      addIfMatched(matched, matchesPerRule, root, matchers, excluded);
      Iterator<EObject> it = root.eAllContents();
      while (it.hasNext()) {
        probed++;
        addIfMatched(matched, matchesPerRule, it.next(), matchers, excluded);
      }
    }

    return new Selection(withoutNestedMatches(matched), matchesPerRule, probed);
  }

  private static void addIfMatched(
      List<Selected> matched,
      Map<ConsistencyRuleId, Integer> matchesPerRule,
      EObject element,
      List<DirtyMatcher> matchers,
      Predicate<EObject> excluded) {
    if (excluded.test(element)) {
      return;
    }

    List<EChange<EObject>> probes = ChangeProbes.forElement(element);
    List<ConsistencyRuleId> matchedBy = new ArrayList<>();
    for (DirtyMatcher dirty : matchers) {
      if (probes.stream().anyMatch(dirty.matcher()::matches)) {
        matchedBy.add(dirty.id());
        matchesPerRule.merge(dirty.id(), 1, Integer::sum);
      }
    }

    if (!matchedBy.isEmpty()) {
      matched.add(Selected.matchedBy(element, matchedBy));
    }
  }

  private static List<Selected> withSourceReferrers(
      List<Selected> affected, List<EObject> sourceRoots, Predicate<EObject> excluded) {
    if (affected.isEmpty()) {
      return affected;
    }

    Set<EObject> subtreeElements = subtreeElementsOf(affected);
    List<Selected> extended = new ArrayList<>(affected);
    for (List<Selected> found = referrersOf(subtreeElements, sourceRoots, excluded);
        !found.isEmpty();
        found = referrersOf(subtreeElements, sourceRoots, excluded)) {
      extended.addAll(found);
      found.forEach(
          selected -> {
            subtreeElements.add(selected.element());
            selected.element().eAllContents().forEachRemaining(subtreeElements::add);
          });
    }

    return withoutNestedMatches(extended);
  }

  private static List<Selected> referrersOf(
      Set<EObject> subtreeElements, List<EObject> sourceRoots, Predicate<EObject> excluded) {
    List<Selected> referrers = new ArrayList<>();
    for (EObject root : sourceRoots) {
      Iterator<EObject> it = root.eAllContents();
      while (it.hasNext()) {
        EObject element = it.next();
        if (subtreeElements.contains(element)
            || excluded.test(element)
            || element.eContainer() == null) {
          continue;
        }

        EObject target = referencedSubtreeElement(element, subtreeElements);
        if (target != null) {
          referrers.add(Selected.referring(element, target));
        }
      }
    }

    return referrers;
  }

  private static EObject referencedSubtreeElement(EObject element, Set<EObject> subtreeElements) {
    for (EReference reference : element.eClass().getEAllReferences()) {
      if (reference.isContainment()
          || reference.isContainer()
          || reference.isDerived()
          || !reference.isChangeable()
          || !element.eIsSet(reference)) {
        continue;
      }

      Object value = element.eGet(reference);
      if (reference.isMany()) {
        for (Object each : (List<?>) value) {
          if (isSubtreeElement(subtreeElements, each)) {
            return (EObject) each;
          }
        }
      } else if (isSubtreeElement(subtreeElements, value)) {
        return (EObject) value;
      }
    }

    return null;
  }

  private static boolean isSubtreeElement(Set<EObject> subtreeElements, Object value) {
    return value instanceof EObject referenced && subtreeElements.contains(referenced);
  }

  private static Set<EObject> subtreeElementsOf(List<Selected> selected) {
    Set<EObject> subtreeElements = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Selected each : selected) {
      subtreeElements.add(each.element());
      each.element().eAllContents().forEachRemaining(subtreeElements::add);
    }

    return subtreeElements;
  }

  private static List<Selected> withoutNestedMatches(List<Selected> matched) {
    Set<EObject> all = Collections.newSetFromMap(new IdentityHashMap<>());
    matched.forEach(selected -> all.add(selected.element()));
    return matched.stream()
        .filter(selected -> !hasMatchedAncestor(all, selected.element()))
        .toList();
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

  record Selection(
      List<Selected> elements,
      Map<ConsistencyRuleId, Integer> matchesPerRule,
      int probedElements) {}

  record Selected(EObject element, List<ConsistencyRuleId> matchedBy, EObject refersTo) {
    static Selected matchedBy(EObject element, List<ConsistencyRuleId> ids) {
      return new Selected(element, List.copyOf(ids), null);
    }

    static Selected referring(EObject element, EObject target) {
      return new Selected(element, List.of(), target);
    }
  }
}
