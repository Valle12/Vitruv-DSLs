package tools.vitruv.dsls.reactions.migration.preservation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;

@RequiredArgsConstructor
final class CandidateCollector {
  private static final String IS_NOW_FORMAT = "%s is now %s";

  private final VsumState oldState;
  private final VsumState newState;
  private final CounterpartIndex counterparts;
  private final PreservationPolicy policy;
  private final Predicate<EObject> derivedRoot;
  private final AmbiguityResolver resolver;
  private final ExternalTargets externalTargets;
  private final List<PreservationCandidate> candidates = new ArrayList<>();
  private final List<OrphanResource> orphanResources = new ArrayList<>();
  private final List<LostItem> losses = new ArrayList<>();
  private final List<DecisionItem> decisions = new ArrayList<>();
  private final Deque<ElementPair> pending = new ArrayDeque<>();
  private final List<DeferredValue> deferredValues = new ArrayList<>();
  private final Map<EObject, List<Object>> signatures = new IdentityHashMap<>();

  private static List<EObject> sameMetaclassCandidates(
      EObject oldElement, List<EObject> candidates) {
    return candidates.stream()
        .filter(candidate -> candidate.eClass() == oldElement.eClass())
        .toList();
  }

  private static boolean isContainment(EStructuralFeature feature) {
    return feature instanceof EReference reference && reference.isContainment();
  }

  private static boolean isPreservedContent(Set<EObject> preservedRoots, EObject target) {
    for (EObject element = target; element != null; element = element.eContainer()) {
      if (preservedRoots.contains(element)) {
        return true;
      }
    }

    return false;
  }

  private Optional<EObject> matchBySignature(EObject oldChild, List<EObject> unmatchedNew) {
    List<Object> signature = signatureOf(oldChild);
    return unmatchedNew.stream()
        .filter(candidate -> signatureOf(candidate).equals(signature))
        .findFirst();
  }

  private List<Object> signatureOf(EObject element) {
    return signatures.computeIfAbsent(element, Elements::shallowSignature);
  }

  CollectedContent collect() {
    oldState.resourceKeys().forEach(this::collectResource);
    while (!pending.isEmpty()) {
      ElementPair pair = pending.poll();
      collectChildren(pair);
      collectValues(pair);
    }

    reconcileDeferredValues();
    return new CollectedContent(
        List.copyOf(candidates),
        List.copyOf(orphanResources),
        List.copyOf(losses),
        List.copyOf(decisions));
  }

  private void collectResource(String resourceKey) {
    List<EObject> oldRoots = oldState.rootsOf(resourceKey);
    if (oldRoots.isEmpty() || !derivedRoot.test(oldRoots.getFirst())) {
      return;
    }

    List<EObject> newRoots = newState.rootsOf(resourceKey);
    List<EObject> matched = new ArrayList<>();
    for (int i = 0; i < oldRoots.size(); i++) {
      matched.add(counterpartOfRoot(oldRoots.get(i), newRoots, i));
    }

    if (matched.stream().allMatch(Objects::isNull)) {
      collectVanishedResource(resourceKey, oldRoots);
      return;
    }

    for (int i = 0; i < oldRoots.size(); i++) {
      pairRoot(resourceKey, oldRoots.get(i), matched.get(i));
    }
  }

  private void pairRoot(String resourceKey, EObject oldRoot, EObject newRoot) {
    if (newRoot == null) {
      reportUserContentOf(oldRoot);
      return;
    }

    noteMove(resourceKey, newRoot);
    counterparts.addCounterpart(oldRoot, newRoot);
    pending.add(new ElementPair(oldRoot, newRoot));
  }

  private void noteMove(String resourceKey, EObject newRoot) {
    String newKey = ElementPaths.resourceKey(newRoot.eResource(), newState.folder());
    if (newKey != null && !newKey.equals(resourceKey)) {
      decisions.add(DecisionItem.moved(resourceKey, "its migrated content is in " + newKey));
    }
  }

  private EObject counterpartOfRoot(EObject oldRoot, List<EObject> newRoots, int index) {
    EObject bridged = counterparts.counterpartOf(oldRoot);
    if (bridged != null) {
      return bridged;
    }

    EObject sameIndex = index < newRoots.size() ? newRoots.get(index) : null;
    if (sameIndex != null && sameIndex.eClass() == oldRoot.eClass()) {
      return sameIndex;
    }

    return movedCounterpartOf(oldRoot);
  }

  private EObject movedCounterpartOf(EObject oldRoot) {
    List<EObject> roots = counterparts.counterpartRootsOf(oldRoot);
    if (roots.size() == 1) {
      return roots.getFirst();
    }

    return roots.isEmpty()
        ? null
        : resolver.chooseCounterpart(oldRoot, sameMetaclassCandidates(oldRoot, roots)).orElse(null);
  }

  private void collectVanishedResource(String resourceKey, List<EObject> oldRoots) {
    if (oldRoots.stream().noneMatch(this::containsCorrespondedElement)) {
      orphanResources.add(new OrphanResource(resourceKey, oldRoots));
      return;
    }

    oldRoots.forEach(this::reportUserContentOf);
  }

  private boolean containsCorrespondedElement(EObject root) {
    if (oldState.isCorresponded(root)) {
      return true;
    }

    for (Iterator<EObject> contents = root.eAllContents(); contents.hasNext(); ) {
      if (oldState.isCorresponded(contents.next())) {
        return true;
      }
    }

    return false;
  }

  private void collectChildren(ElementPair pair) {
    List<EObject> unmatchedNew = new ArrayList<>(pair.newElement().eContents());
    List<EObject> unpairedOld = new ArrayList<>();
    for (EObject oldChild : pair.oldElement().eContents()) {
      EObject counterpart = counterparts.counterpartOf(oldChild);
      if (counterpart == null) {
        unpairedOld.add(oldChild);
      } else {
        unmatchedNew.removeIf(candidate -> candidate == counterpart);
        pending.add(new ElementPair(oldChild, counterpart));
      }
    }

    for (EObject oldChild : unpairedOld) {
      ChildMatch match = matchOf(oldChild, unmatchedNew);
      if (match.counterpart() != null) {
        unmatchedNew.removeIf(candidate -> candidate == match.counterpart());
        counterparts.addCounterpart(oldChild, match.counterpart());
        pending.add(new ElementPair(oldChild, match.counterpart()));
      } else if (!match.open()) {
        collectUnmatchedChild(pair.newElement(), oldChild);
      }
    }
  }

  private ChildMatch matchOf(EObject oldChild, List<EObject> unmatchedNew) {
    Optional<EObject> optionalBySignature = matchBySignature(oldChild, unmatchedNew);
    if (optionalBySignature.isPresent()) {
      return new ChildMatch(optionalBySignature.get(), false);
    }

    List<EObject> localCandidates = sameMetaclassCandidates(oldChild, unmatchedNew);
    return localCandidates.isEmpty()
        ? new ChildMatch(null, false)
        : chosenCounterpart(oldChild, localCandidates);
  }

  private ChildMatch chosenCounterpart(EObject oldElement, List<EObject> candidates) {
    EObject chosen = resolver.chooseCounterpart(oldElement, candidates).orElse(null);
    if (chosen != null || resolver.resolves()) {
      return new ChildMatch(chosen, false);
    }

    losses.add(
        new LostItem(
            describe(oldElement),
            LossReason.AMBIGUOUS_COUNTERPART,
            describeAlternatives(candidates)));
    return new ChildMatch(null, true);
  }

  private String describeAlternatives(List<EObject> candidates) {
    return "it could be "
        + candidates.stream()
            .map(candidate -> Elements.describe(candidate, newState.folder()))
            .toList();
  }

  private void collectUnmatchedChild(EObject newOwner, EObject oldChild) {
    boolean rulesOwnIt = oldState.isCorresponded(oldChild) && policy.skipsRuleProducedContent();
    if (rulesOwnIt && userContentDetail(oldChild) == null) {
      losses.add(new LostItem(describe(oldChild), LossReason.RULE_NO_LONGER_DERIVES, null));
      return;
    }

    PreservationCandidate.Kind kind =
        rulesOwnIt ? PreservationCandidate.Kind.CARRIER : PreservationCandidate.Kind.NORMAL;
    EReference containment = oldChild.eContainmentFeature();
    targetFeatureFor(newOwner, containment, oldChild, describe(oldChild))
        .ifPresent(
            target ->
                candidates.add(
                    new PreservationCandidate(
                        oldChild.eContainer(), containment, oldChild, newOwner, target, kind)));
  }

  private String userContentDetail(EObject droppedElement) {
    long userWritten = 0;
    for (Iterator<EObject> contents = droppedElement.eAllContents(); contents.hasNext(); ) {
      if (!oldState.isCorresponded(contents.next())) {
        userWritten++;
      }
    }

    return userWritten == 0
        ? null
        : "it contains %d element(s) that no rule produced".formatted(userWritten);
  }

  private void collectValues(ElementPair pair) {
    for (EStructuralFeature feature : pair.oldElement().eClass().getEAllStructuralFeatures()) {
      if (isContainment(feature)
          || !Elements.isTransplantable(feature)
          || keepsItsOwnMultiValuedAttribute(pair, feature)) {
        continue;
      }

      for (Object oldValue : Elements.valuesOf(pair.oldElement(), feature)) {
        collectValue(pair, feature, oldValue);
      }
    }
  }

  private boolean keepsItsOwnMultiValuedAttribute(ElementPair pair, EStructuralFeature feature) {
    if (!(feature instanceof EAttribute) || !feature.isMany()) {
      return false;
    }

    List<Object> newValues = Elements.valuesOf(pair.newElement(), feature);
    if (newValues.isEmpty()) {
      return false;
    }

    List<Object> oldValues = Elements.valuesOf(pair.oldElement(), feature);
    if (!Elements.sameValues(oldValues, newValues)) {
      decisions.add(
          DecisionItem.replaced(
              describeValueOf(pair, feature),
              IS_NOW_FORMAT.formatted(feature.getName(), newValues)));
    }

    return true;
  }

  private void collectValue(ElementPair pair, EStructuralFeature feature, Object oldValue) {
    if (externalTargets.isExternal(oldValue)) {
      reportExternalValue(pair, feature, (EObject) oldValue);
      return;
    }

    Object value = mapValue(oldValue);
    if (value == null) {
      deferredValues.add(new DeferredValue(pair, feature, (EObject) oldValue));
      return;
    }

    collectValueInto(pair, feature, oldValue, value);
  }

  private void collectValueInto(
      ElementPair pair, EStructuralFeature feature, Object oldValue, Object value) {
    targetFeatureFor(pair.newElement(), feature, value, describeValueOf(pair, feature))
        .map(target -> new TargetSlot(pair.newElement(), target))
        .filter(slot -> !SlotResolver.holds(slot, value))
        .ifPresent(slot -> collectMissingValue(pair, feature, oldValue, slot));
  }

  private void reconcileDeferredValues() {
    Set<EObject> preservedRoots = preservedRoots();
    for (DeferredValue deferred : deferredValues) {
      if (isPreservedContent(preservedRoots, deferred.oldTarget())) {
        collectValueInto(
            deferred.pair(), deferred.feature(), deferred.oldTarget(), deferred.oldTarget());
      } else {
        losses.add(
            new LostItem(
                describeValueOf(deferred.pair(), deferred.feature()),
                LossReason.UNRESOLVABLE_REFERENCE,
                "the value %s has no counterpart".formatted(describe(deferred.oldTarget()))));
      }
    }
  }

  private Set<EObject> preservedRoots() {
    Set<EObject> roots = Collections.newSetFromMap(new IdentityHashMap<>());
    for (PreservationCandidate candidate : candidates) {
      if (candidate.isContainment()) {
        roots.add((EObject) candidate.oldValue());
      }
    }

    orphanResources.forEach(orphan -> roots.addAll(orphan.roots()));
    return roots;
  }

  private Optional<EStructuralFeature> targetFeatureFor(
      EObject newOwner, EStructuralFeature oldFeature, Object value, String subject) {
    SlotResolution located = SlotResolver.findSlot(newOwner, oldFeature, value);
    if (located.isResolved()) {
      return Optional.of(located.slot().feature());
    }

    Optional<EStructuralFeature> chosen =
        located.reason() == LossReason.AMBIGUOUS_FEATURE
            ? resolver.chooseFeature(subject, newOwner, located.alternatives())
            : Optional.empty();
    if (chosen.isEmpty()) {
      losses.add(new LostItem(subject, located.reason(), located.detail()));
    }

    return chosen;
  }

  private void collectMissingValue(
      ElementPair pair, EStructuralFeature feature, Object oldValue, TargetSlot slot) {
    if (rulesOwnValuesOf(pair.oldElement())) {
      recordRuleOwnedValue(pair, feature, slot);
      return;
    }

    SlotResolution room = SlotResolver.checkCapacity(slot);
    if (!room.isResolved()) {
      if (room.reason() == LossReason.SLOT_OCCUPIED
          && !oldState.isCorresponded(pair.oldElement())) {
        candidates.add(
            new PreservationCandidate(
                pair.oldElement(),
                feature,
                oldValue,
                pair.newElement(),
                slot.feature(),
                PreservationCandidate.Kind.OVERWRITE));
        return;
      }

      losses.add(valueLoss(pair, feature, room));
      return;
    }

    candidates.add(
        new PreservationCandidate(
            pair.oldElement(), feature, oldValue, pair.newElement(), slot.feature()));
  }

  private void recordRuleOwnedValue(ElementPair pair, EStructuralFeature feature, TargetSlot slot) {
    EStructuralFeature target = slot.feature();
    if (!target.isMany() && slot.owner().eIsSet(target)) {
      decisions.add(
          DecisionItem.replaced(
              describeValueOf(pair, feature),
              IS_NOW_FORMAT.formatted(target.getName(), describeNew(slot.owner().eGet(target)))));
      return;
    }

    losses.add(
        new LostItem(
            describeValueOf(pair, feature),
            LossReason.RULE_NO_LONGER_DERIVES,
            "the new rules leave %s.%s unset"
                .formatted(Elements.typeOf(pair.newElement()), target.getName())));
  }

  private boolean rulesOwnValuesOf(EObject oldElement) {
    return oldState.isCorresponded(oldElement) && policy.skipsRuleProducedContent();
  }

  private String describeValueOf(ElementPair pair, EStructuralFeature feature) {
    return describe(pair.oldElement()) + "." + feature.getName();
  }

  private String describeNew(Object value) {
    return value instanceof EObject element
        ? Elements.describe(element, newState.folder())
        : String.valueOf(value);
  }

  private LostItem valueLoss(
      ElementPair pair, EStructuralFeature feature, SlotResolution rejection) {
    return new LostItem(describeValueOf(pair, feature), rejection.reason(), rejection.detail());
  }

  private void reportExternalValue(
      ElementPair pair, EStructuralFeature feature, EObject oldTarget) {
    String oldIdentity = externalTargets.identityOf(oldTarget);
    if (oldIdentity == null) {
      return;
    }

    Optional<EStructuralFeature> target =
        targetFeatureFor(pair.newElement(), feature, oldTarget, describeValueOf(pair, feature));
    if (target.isEmpty()) {
      return;
    }

    List<String> newIdentities = externalIdentitiesOf(pair.newElement(), target.get());
    if (newIdentities.contains(oldIdentity)) {
      return;
    }

    String subject = describeValueOf(pair, feature);
    if (newIdentities.isEmpty()) {
      losses.add(
          new LostItem(
              subject,
              LossReason.RULE_NO_LONGER_DERIVES,
              "it referenced %s, and the new rules leave the reference empty"
                  .formatted(oldIdentity)));
      return;
    }

    decisions.add(
        DecisionItem.replaced(
            subject, IS_NOW_FORMAT.formatted(oldIdentity, String.join(", ", newIdentities))));
  }

  private List<String> externalIdentitiesOf(EObject newElement, EStructuralFeature feature) {
    List<String> identities = new ArrayList<>();
    for (Object value : Elements.valuesOf(newElement, feature)) {
      if (value instanceof EObject target) {
        String identity = externalTargets.identityOf(target);
        if (identity != null) {
          identities.add(identity);
        }
      }
    }

    return identities;
  }

  private Object mapValue(Object oldValue) {
    if (!(oldValue instanceof EObject target)) {
      return oldValue;
    }

    EObject counterpart = counterparts.counterpartOf(target);
    if (counterpart != null) {
      return counterpart;
    }

    return ElementPaths.resourceKey(target.eResource(), oldState.folder()) == null ? target : null;
  }

  private void reportUserContentOf(EObject root) {
    if (!oldState.isCorresponded(root)) {
      losses.add(LostItem.of(describe(root), LossReason.NO_COUNTERPART_RESOURCE));
      return;
    }

    for (Iterator<EObject> contents = root.eAllContents(); contents.hasNext(); ) {
      EObject element = contents.next();
      if (!oldState.isCorresponded(element) && !hasReportedAncestor(element)) {
        losses.add(LostItem.of(describe(element), LossReason.NO_COUNTERPART_RESOURCE));
      }
    }
  }

  private boolean hasReportedAncestor(EObject element) {
    for (EObject container = element.eContainer();
        container != null;
        container = container.eContainer()) {
      if (!oldState.isCorresponded(container)) {
        return true;
      }
    }

    return false;
  }

  private String describe(EObject element) {
    return Elements.describe(element, oldState.folder());
  }

  private record ElementPair(EObject oldElement, EObject newElement) {}

  private record ChildMatch(EObject counterpart, boolean open) {}

  private record DeferredValue(ElementPair pair, EStructuralFeature feature, EObject oldTarget) {}
}
