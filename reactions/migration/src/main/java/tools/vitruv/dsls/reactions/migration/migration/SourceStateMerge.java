package tools.vitruv.dsls.reactions.migration.migration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.eclipse.emf.common.util.BasicMonitor;
import org.eclipse.emf.compare.AttributeChange;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.compare.DifferenceKind;
import org.eclipse.emf.compare.EMFCompare;
import org.eclipse.emf.compare.ReferenceChange;
import org.eclipse.emf.compare.ResourceAttachmentChange;
import org.eclipse.emf.compare.match.impl.MatchEngineFactoryImpl;
import org.eclipse.emf.compare.match.impl.MatchEngineFactoryRegistryImpl;
import org.eclipse.emf.compare.merge.BatchMerger;
import org.eclipse.emf.compare.merge.IMerger;
import org.eclipse.emf.compare.scope.DefaultComparisonScope;
import org.eclipse.emf.compare.utils.UseIdentifiers;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;

final class SourceStateMerge {
  private static final EMFCompare COMPARE = structuralCompare();
  private static final IMerger.Registry MERGERS = IMerger.RegistryImpl.createStandaloneInstance();

  private SourceStateMerge() {}

  private static EMFCompare structuralCompare() {
    var matchEngineRegistry = MatchEngineFactoryRegistryImpl.createStandaloneInstance();
    matchEngineRegistry.add(new MatchEngineFactoryImpl(UseIdentifiers.NEVER));
    return EMFCompare.builder().setMatchEngineFactoryRegistry(matchEngineRegistry).build();
  }

  static Delta between(Resource liveState, Resource desiredState) {
    Comparison comparison =
        COMPARE.compare(new DefaultComparisonScope(desiredState, liveState, null));
    List<Diff> additions = new ArrayList<>();
    List<Diff> retractions = new ArrayList<>();
    for (Diff difference : comparison.getDifferences()) {
      if (difference instanceof ResourceAttachmentChange) {
        continue;
      }

      if (difference.getKind() == DifferenceKind.DELETE) {
        retractions.add(difference);
      } else {
        additions.add(difference);
      }
    }

    return new Delta(additions, retractions);
  }

  static Summary count(Collection<Resource> liveState, Collection<Resource> desiredState) {
    Map<String, Resource> liveByName = ModelStateDiff.byFileName(liveState);
    Map<String, Resource> desiredByName = ModelStateDiff.byFileName(desiredState);
    long additions = 0;
    long retractions = 0;
    for (String name : new LinkedHashSet<>(desiredByName.keySet())) {
      Resource live = liveByName.get(name);
      if (live == null) {
        additions += countContents(desiredByName.get(name));
        continue;
      }

      Delta delta = between(live, desiredByName.get(name));
      additions += delta.additions().size();
      retractions += delta.retractions().size();
    }

    return new Summary(additions, retractions);
  }

  private static long countContents(Resource resource) {
    long elements = 0;
    for (var contents = resource.getAllContents(); contents.hasNext(); contents.next()) {
      elements++;
    }

    return elements;
  }

  private static String describe(Diff retraction) {
    Object value =
        switch (retraction) {
          case ReferenceChange reference -> reference.getValue();
          case AttributeChange attribute -> attribute.getValue();
          default -> null;
        };
    if (value instanceof EObject element) {
      return element.eClass().getName() + " at " + EcoreUtil.getURI(element);
    }

    return value == null ? retraction.toString() : String.valueOf(value);
  }

  record Summary(long additions, long retractions) {
    long total() {
      return additions + retractions;
    }
  }

  record Delta(List<Diff> additions, List<Diff> retractions) {
    boolean isEmpty() {
      return additions.isEmpty() && retractions.isEmpty();
    }

    List<String> describeRetractions() {
      return retractions.stream().map(SourceStateMerge::describe).toList();
    }

    void apply() {
      if (isEmpty()) {
        return;
      }

      List<Diff> all = new ArrayList<>(additions);
      all.addAll(retractions);
      new BatchMerger(MERGERS).copyAllLeftToRight(all, new BasicMonitor());
    }
  }
}
