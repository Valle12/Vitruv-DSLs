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

/**
 * Which element of the migrated models continues which element of the state before the
 * migration. The pairing goes through the correspondences of both states, so two elements are
 * counterparts when the same element of the dominant model answers for them.
 */
public final class CounterpartIndex {
  private final Map<EObject, EObject> counterparts;

  private CounterpartIndex(Map<EObject, EObject> counterparts) {
    this.counterparts = counterparts;
  }

  /**
   * Pairs the derived elements of the old state with those of the new one. An element of the
   * new state is claimed by at most one old element, and where several could be the counterpart
   * the resolver is asked.
   *
   * @param oldState the state from before the migration
   * @param newState the state the migration produced
   * @param derivedRoot decides whether a root belongs to a model that was re-derived
   * @param resolver what settles an ambiguous pairing, or records it as undecided
   * @return the pairing of the two states
   */
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
    for (VsumState.Partner oldLink : oldState.partnersOf(oldElement)) {
      EObject newSource = sameSourceElementIn(newState, oldLink.element(), oldState);
      if (newSource == null) {
        continue;
      }

      List<EObject> candidates =
          derivedPartners(newState, newSource, derivedRoot, oldLink.tag()).stream()
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
      VsumState newState, EObject newSource, Predicate<EObject> derivedRoot, String tag) {
    return newState.partnersOf(newSource).stream()
        .filter(partner -> partner.tag().equals(tag))
        .map(VsumState.Partner::element)
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

  /**
   * Returns the roots of the migrated models that continue the given old root.
   *
   * @param oldRoot a root of the state from before the migration
   * @return its counterpart roots, empty when the migration derives none
   */
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

  /**
   * Returns the migrated element that continues the given old one.
   *
   * @param oldElement an element of the state from before the migration
   * @return its counterpart, or {@code null} when it has none
   */
  public EObject counterpartOf(EObject oldElement) {
    return counterparts.get(oldElement);
  }

  /**
   * Records a pairing the index did not find by itself, such as one an element restored by the
   * preservation step establishes.
   *
   * @param oldElement an element of the state from before the migration
   * @param newElement the migrated element that continues it
   */
  public void addCounterpart(EObject oldElement, EObject newElement) {
    counterparts.put(oldElement, newElement);
  }

  /**
   * Returns how many elements were paired.
   *
   * @return the number of counterparts
   */
  public int size() {
    return counterparts.size();
  }
}
