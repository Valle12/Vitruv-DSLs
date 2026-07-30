package tools.vitruv.dsls.reactions.migration.preservation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

public final class CounterpartIndex {
  private final Map<EObject, EObject> counterparts;

  private CounterpartIndex(Map<EObject, EObject> counterparts) {
    this.counterparts = counterparts;
  }

  public static CounterpartIndex build(
      VsumState oldState,
      VsumState newState,
      Predicate<EObject> derivedRoot,
      AmbiguityResolver resolver) {
    Map<EObject, EObject> counterparts = new IdentityHashMap<>();
    Set<EObject> claimed = Collections.newSetFromMap(new IdentityHashMap<>());
    for (EObject oldElement : derivedElementsOf(oldState, derivedRoot)) {
      bridge(oldElement, oldState, newState, derivedRoot, claimed, resolver)
          .ifPresent(
              counterpart -> {
                claimed.add(counterpart);
                counterparts.put(oldElement, counterpart);
              });
    }

    return new CounterpartIndex(counterparts);
  }

  static List<EObject> derivedElementsOf(VsumState state, Predicate<EObject> derivedRoot) {
    List<EObject> elements = new ArrayList<>();
    for (String resourceKey : state.resourceKeys()) {
      for (EObject root : state.rootsOf(resourceKey)) {
        if (!derivedRoot.test(root)) {
          continue;
        }

        elements.add(root);
        Iterator<EObject> it = root.eAllContents();
        while (it.hasNext()) {
          elements.add(it.next());
        }
      }
    }

    return elements;
  }

  private static Optional<EObject> bridge(
      EObject oldElement,
      VsumState oldState,
      VsumState newState,
      Predicate<EObject> derivedRoot,
      Set<EObject> claimed,
      AmbiguityResolver resolver) {
    for (EObject oldSource : oldState.partnersOf(oldElement)) {
      EObject newSource = sameSourceElementIn(newState, oldSource, oldState);
      if (newSource == null) {
        continue;
      }

      List<EObject> candidates =
          derivedPartners(newState, newSource, derivedRoot).stream()
              .filter(partner -> !claimed.contains(partner))
              .toList();
      Optional<EObject> counterpart = pickCounterpart(candidates, oldElement, resolver);
      if (counterpart.isPresent()) {
        return counterpart;
      }
    }

    return Optional.empty();
  }

  private static EObject sameSourceElementIn(
      VsumState newState, EObject oldSource, VsumState oldState) {
    String resourceKey = ElementPaths.resourceKey(oldSource.eResource(), oldState.folder());
    if (resourceKey == null) {
      return null;
    }

    EObject newSource =
        ElementPaths.locate(newState.rootsOf(resourceKey), ElementPaths.pathOf(oldSource));
    return newSource != null && newSource.eClass() == oldSource.eClass() ? newSource : null;
  }

  private static List<EObject> derivedPartners(
      VsumState newState, EObject newSource, Predicate<EObject> derivedRoot) {
    return newState.partnersOf(newSource).stream()
        .filter(partner -> derivedRoot.test(EcoreUtil.getRootContainer(partner)))
        .toList();
  }

  private static Optional<EObject> pickCounterpart(
      List<EObject> candidates, EObject oldElement, AmbiguityResolver resolver) {
    List<EObject> sameMetaclass =
        candidates.stream().filter(candidate -> candidate.eClass() == oldElement.eClass()).toList();
    if (sameMetaclass.size() == 1) {
      return Optional.of(sameMetaclass.getFirst());
    }

    if (candidates.size() == 1) {
      return Optional.of(candidates.getFirst());
    }

    return resolver.chooseCounterpart(oldElement, sameMetaclass);
  }

  public List<EObject> counterpartRootsOf(EObject oldRoot) {
    Map<EObject, Integer> votes = new IdentityHashMap<>();
    List<EObject> voted = new ArrayList<>();
    Iterator<EObject> it = oldRoot.eAllContents();
    while (it.hasNext()) {
      EObject counterpart = counterparts.get(it.next());
      if (counterpart != null
          && votes.merge(EcoreUtil.getRootContainer(counterpart), 1, Integer::sum) == 1) {
        voted.add(EcoreUtil.getRootContainer(counterpart));
      }
    }

    if (voted.size() <= 1) {
      return List.copyOf(voted);
    }

    int most = voted.stream().mapToInt(votes::get).max().orElse(0);
    return voted.stream().filter(root -> votes.get(root) == most).toList();
  }

  public EObject counterpartOf(EObject oldElement) {
    return counterparts.get(oldElement);
  }

  public void addCounterpart(EObject oldElement, EObject newElement) {
    counterparts.put(oldElement, newElement);
  }

  public int size() {
    return counterparts.size();
  }
}
