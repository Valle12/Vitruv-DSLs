package tools.vitruv.dsls.reactions.migration.preservation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.EcoreUtil.Copier;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;

@RequiredArgsConstructor
final class TransplantApplier {
  private static final String REFERENCES_CONTENT_NOT_KEPT =
      "it references content that could not be kept";

  private final TransplantTarget target;
  private final CounterpartIndex counterparts;
  private final AdapterRegistry adapters;
  private final Path oldFolder;
  private final Path vsumFolder;
  private final Predicate<EObject> ruleProduced;
  private final List<PreservedItem> preserved = new ArrayList<>();
  private final List<LostItem> lost = new ArrayList<>();
  private final List<DecisionItem> decisions = new ArrayList<>();
  private final MetamodelValidation validation = new MetamodelValidation();

  @SuppressWarnings("unchecked")
  private static void insert(TargetSlot slot, Object value) {
    if (slot.feature().isMany()) {
      ((List<Object>) slot.owner().eGet(slot.feature())).add(value);
    } else {
      slot.owner().eSet(slot.feature(), value);
    }
  }

  private static void remove(TargetSlot slot, Object value) {
    if (slot.feature().isMany()) {
      ((List<?>) slot.owner().eGet(slot.feature())).remove(value);
    } else {
      slot.owner().eUnset(slot.feature());
    }
  }

  private static Set<EObject> newIdentitySet() {
    return Collections.newSetFromMap(new IdentityHashMap<>());
  }

  private static void addSubtree(EObject root, Consumer<EObject> collector) {
    collector.accept(root);
    root.eAllContents().forEachRemaining(collector);
  }

  private static void addSubtree(EObject root, Set<EObject> elements) {
    addSubtree(root, elements::add);
  }

  @SuppressWarnings("unchecked")
  private static boolean cannotSever(EObject element, EReference reference, Set<EObject> dropped) {
    if (reference.isMany()) {
      List<EObject> values = (List<EObject>) element.eGet(reference);
      return values.removeIf(dropped::contains) && values.size() < reference.getLowerBound();
    }

    if (!(element.eGet(reference) instanceof EObject referenced) || !dropped.contains(referenced)) {
      return false;
    }

    if (reference.getLowerBound() > 0) {
      return true;
    }

    element.eUnset(reference);
    return false;
  }

  void apply(CollectedContent content) {
    Copier copier = new Copier();
    List<PendingCandidate> candidates = copyCandidates(content.candidates(), copier);
    List<PendingOrphan> orphans = copyOrphans(content.orphanResources(), copier);
    copier.copyReferences();
    ReferenceRebinder rebinder =
        new ReferenceRebinder(
            this::migratedElementOf, oldFolder, copier, pruneCarriers(candidates, copier));
    Set<EObject> dropped = rebindAll(candidates, orphans, rebinder);
    severDroppedContent(candidates, orphans, dropped);
    Set<EObject> rolledBack = insertCandidates(candidates, rebinder, dropped);
    severDroppedContent(candidates, orphans, rolledBack);
    restoreOrphans(orphans);
  }

  List<PreservedItem> preserved() {
    return List.copyOf(preserved);
  }

  List<LostItem> lost() {
    return List.copyOf(lost);
  }

  List<DecisionItem> decisions() {
    return List.copyOf(decisions);
  }

  private List<PendingCandidate> copyCandidates(
      List<PreservationCandidate> collected, Copier copier) {
    List<PendingCandidate> pending = new ArrayList<>();
    for (PreservationCandidate candidate : collected) {
      EObject owner = target.locate(candidate.newOwner());
      if (owner == null) {
        lost.add(loss(candidate, LossReason.NO_COUNTERPART_CONTAINER, null));
        continue;
      }

      EObject copy = candidate.isContainment() ? copier.copy((EObject) candidate.oldValue()) : null;
      pending.add(
          new PendingCandidate(candidate, new TargetSlot(owner, candidate.targetFeature()), copy));
    }

    return pending;
  }

  private List<PendingOrphan> copyOrphans(List<OrphanResource> collected, Copier copier) {
    List<PendingOrphan> pending = new ArrayList<>();
    for (OrphanResource orphan : collected) {
      pending.add(new PendingOrphan(orphan, new ArrayList<>(copier.copyAll(orphan.roots()))));
    }

    return pending;
  }

  private Map<EObject, EObject> pruneCarriers(List<PendingCandidate> candidates, Copier copier) {
    Map<EObject, EObject> pruned = new IdentityHashMap<>();
    for (PendingCandidate pending : candidates) {
      if (pending.candidate.kind() == PreservationCandidate.Kind.CARRIER) {
        pruneRuleOnlyContent((EObject) pending.candidate.oldValue(), copier, pruned);
      }
    }

    return pruned;
  }

  private void pruneRuleOnlyContent(EObject original, Copier copier, Map<EObject, EObject> pruned) {
    for (EObject child : List.copyOf(original.eContents())) {
      if (containsUserContent(child)) {
        pruneRuleOnlyContent(child, copier, pruned);
      } else {
        EcoreUtil.remove(copier.get(child));
        rememberPruned(child, copier, pruned);
      }
    }
  }

  private void rememberPruned(EObject original, Copier copier, Map<EObject, EObject> pruned) {
    pruned.put(copier.get(original), original);
    for (Iterator<EObject> it = original.eAllContents(); it.hasNext(); ) {
      EObject descendant = it.next();
      pruned.put(copier.get(descendant), descendant);
    }
  }

  private boolean containsUserContent(EObject element) {
    if (!ruleProduced.test(element)) {
      return true;
    }

    for (Iterator<EObject> it = element.eAllContents(); it.hasNext(); ) {
      if (!ruleProduced.test(it.next())) {
        return true;
      }
    }

    return false;
  }

  private Set<EObject> rebindAll(
      List<PendingCandidate> candidates, List<PendingOrphan> orphans, ReferenceRebinder rebinder) {
    Set<EObject> dropped = newIdentitySet();
    for (PendingCandidate pending : candidates) {
      if (pending.copy != null && rebinder.rebindFailed(pending.copy)) {
        dropCandidate(pending, null, dropped);
      }
    }

    for (PendingOrphan pending : orphans) {
      if (pending.copies.stream().anyMatch(rebinder::rebindFailed)) {
        dropOrphan(pending, "restoring the file", dropped);
      }
    }

    return dropped;
  }

  private void severDroppedContent(
      List<PendingCandidate> candidates, List<PendingOrphan> orphans, Set<EObject> dropped) {
    boolean changed = !dropped.isEmpty();
    while (changed) {
      changed = dropCandidatesReferencingDropped(candidates, dropped);
      changed |= dropOrphansReferencingDropped(orphans, dropped);
    }
  }

  private boolean dropCandidatesReferencingDropped(
      List<PendingCandidate> candidates, Set<EObject> dropped) {
    boolean changed = false;
    for (PendingCandidate pending : candidates) {
      if (pending.dropped || pending.copy == null) {
        continue;
      }

      if (cannotSeverReferencesInto(pending.copy, dropped)) {
        if (pending.inserted) {
          remove(pending.slot, pending.copy);
          unkeep(pending.candidate);
          pending.inserted = false;
        }

        dropCandidate(pending, REFERENCES_CONTENT_NOT_KEPT, dropped);
        changed = true;
      }
    }

    return changed;
  }

  private boolean dropOrphansReferencingDropped(List<PendingOrphan> orphans, Set<EObject> dropped) {
    boolean changed = false;
    for (PendingOrphan pending : orphans) {
      if (pending.dropped) {
        continue;
      }

      if (pending.copies.stream().anyMatch(copy -> cannotSeverReferencesInto(copy, dropped))) {
        dropOrphan(pending, REFERENCES_CONTENT_NOT_KEPT, dropped);
        changed = true;
      }
    }

    return changed;
  }

  private boolean cannotSeverReferencesInto(EObject copyRoot, Set<EObject> dropped) {
    List<EObject> elements = new ArrayList<>();
    addSubtree(copyRoot, elements::add);
    for (EObject element : elements) {
      for (EReference reference : element.eClass().getEAllReferences()) {
        if (!reference.isContainment()
            && Elements.isTransplantable(reference)
            && cannotSever(element, reference, dropped)) {
          return true;
        }
      }
    }

    return false;
  }

  private void dropCandidate(PendingCandidate pending, String detail, Set<EObject> dropped) {
    pending.dropped = true;
    lost.add(loss(pending.candidate, LossReason.UNRESOLVABLE_REFERENCE, detail));
    if (pending.copy != null) {
      addSubtree(pending.copy, dropped);
    }
  }

  private void dropOrphan(PendingOrphan pending, String detail, Set<EObject> dropped) {
    pending.dropped = true;
    lost.add(new LostItem(pending.orphan.resourceKey(), LossReason.UNRESOLVABLE_REFERENCE, detail));
    pending.copies.forEach(copy -> addSubtree(copy, dropped));
  }

  private Set<EObject> insertCandidates(
      List<PendingCandidate> candidates, ReferenceRebinder rebinder, Set<EObject> dropped) {
    Set<EObject> rolledBack = newIdentitySet();
    for (PendingCandidate pending : candidates) {
      if (!pending.dropped) {
        Object value = resolveValue(pending, rebinder, dropped);
        if (value != null) {
          insertPending(pending, value, rolledBack);
        }
      }
    }

    return rolledBack;
  }

  private Object resolveValue(
      PendingCandidate pending, ReferenceRebinder rebinder, Set<EObject> dropped) {
    if (pending.copy != null) {
      prepareForView(pending.copy, pending.slot.owner().eResource().getResourceSet());
      return pending.copy;
    }

    Object oldValue = pending.candidate.oldValue();
    if (!(oldValue instanceof EObject oldTarget)) {
      return oldValue;
    }

    EObject resolved = rebinder.rebindTarget(oldTarget);
    if (resolved == null) {
      dropCandidate(pending, null, dropped);
      return null;
    }

    if (dropped.contains(resolved)) {
      dropCandidate(pending, REFERENCES_CONTENT_NOT_KEPT, dropped);
      return null;
    }

    return resolved;
  }

  private void insertPending(PendingCandidate pending, Object value, Set<EObject> rolledBack) {
    TargetSlot slot = pending.slot;
    if (SlotResolver.holds(slot, value)) {
      return;
    }

    if (pending.candidate.kind() == PreservationCandidate.Kind.OVERWRITE
        && !slot.feature().isMany()
        && slot.owner().eIsSet(slot.feature())) {
      overwrite(pending, value);
      return;
    }

    SlotResolution room = SlotResolver.checkCapacity(slot);
    if (!room.isResolved()) {
      lost.add(loss(pending.candidate, room.reason(), room.detail()));
      return;
    }

    validation.remember(slot.owner(), pending.candidate.oldValue());
    insert(slot, value);
    if (validation.regressed(slot.owner(), pending.candidate.oldValue(), value)) {
      remove(slot, value);
      lost.add(loss(pending.candidate, LossReason.VALIDATION_REJECTED, null));
      if (value instanceof EObject element) {
        addSubtree(element, rolledBack);
      }

      return;
    }

    pending.inserted = true;
    keep(pending.candidate, slot);
    if (pending.candidate.kind() == PreservationCandidate.Kind.CARRIER) {
      decisions.add(
          DecisionItem.carrier(
              Elements.describe(pending.candidate, oldFolder),
              "the parts only the old rules derived were left out"));
    }
  }

  private void overwrite(PendingCandidate pending, Object value) {
    TargetSlot slot = pending.slot;
    Object replaced = slot.owner().eGet(slot.feature());
    validation.remember(slot.owner(), pending.candidate.oldValue());
    slot.owner().eSet(slot.feature(), value);
    if (validation.regressed(slot.owner(), pending.candidate.oldValue(), value)) {
      slot.owner().eSet(slot.feature(), replaced);
      lost.add(loss(pending.candidate, LossReason.VALIDATION_REJECTED, null));
      return;
    }

    pending.inserted = true;
    keep(pending.candidate, slot);
    decisions.add(
        DecisionItem.overridden(
            Elements.describe(pending.candidate, oldFolder),
            "it replaced %s".formatted(describeReplaced(replaced))));
  }

  private void keep(PreservationCandidate candidate, TargetSlot slot) {
    preserved.add(
        new PreservedItem(
            Elements.describe(candidate, oldFolder),
            Elements.describe(candidate.newOwner(), vsumFolder),
            slot.feature().getName()));
  }

  private void unkeep(PreservationCandidate candidate) {
    String element = Elements.describe(candidate, oldFolder);
    for (int i = preserved.size() - 1; i >= 0; i--) {
      if (preserved.get(i).element().equals(element)) {
        preserved.remove(i);
        return;
      }
    }
  }

  private String describeReplaced(Object value) {
    return value instanceof EObject element
        ? Elements.describe(element, vsumFolder)
        : String.valueOf(value);
  }

  private void restoreOrphans(List<PendingOrphan> orphans) {
    for (PendingOrphan pending : orphans) {
      if (pending.dropped) {
        continue;
      }

      ResourceSet targetResourceSet = target.resourceSet();
      pending.copies.forEach(copy -> prepareForView(copy, targetResourceSet));
      Resource created =
          target.registerRoot(
              pending.copies.getFirst(),
              URI.createFileURI(vsumFolder.resolve(pending.orphan.resourceKey()).toString()));
      for (int i = 1; i < pending.copies.size(); i++) {
        created.getContents().add(pending.copies.get(i));
      }

      preserved.add(
          new PreservedItem(pending.orphan.resourceKey(), vsumFolder.toString(), "model file"));
    }
  }

  private void prepareForView(EObject copy, ResourceSet viewResourceSet) {
    if (viewResourceSet != null) {
      adapters.adapterFor(copy).prepareForReplayInto(viewResourceSet, List.of(copy));
    }
  }

  private EObject migratedElementOf(EObject oldElement) {
    EObject counterpart = counterparts.counterpartOf(oldElement);
    return counterpart == null ? null : target.locate(counterpart);
  }

  private LostItem loss(PreservationCandidate candidate, LossReason reason, String detail) {
    return new LostItem(Elements.describe(candidate, oldFolder), reason, detail);
  }

  private static final class PendingCandidate {
    private final PreservationCandidate candidate;
    private final TargetSlot slot;
    private final EObject copy;
    private boolean dropped;
    private boolean inserted;

    private PendingCandidate(PreservationCandidate candidate, TargetSlot slot, EObject copy) {
      this.candidate = candidate;
      this.slot = slot;
      this.copy = copy;
    }
  }

  private static final class PendingOrphan {
    private final OrphanResource orphan;
    private final List<EObject> copies;
    private boolean dropped;

    private PendingOrphan(OrphanResource orphan, List<EObject> copies) {
      this.orphan = orphan;
      this.copies = copies;
    }
  }
}
