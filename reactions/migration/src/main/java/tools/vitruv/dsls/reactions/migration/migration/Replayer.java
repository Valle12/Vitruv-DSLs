package tools.vitruv.dsls.reactions.migration.migration;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.vsum.ModelSnapshot;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

/**
 * Inserts a snapshot into a V-SUM by committing its roots through a view, so that the rules of
 * the new rule set derive the other models from them.
 */
@RequiredArgsConstructor
public class Replayer {
  private final AdapterRegistry adapters;

  @SuppressWarnings("UnstableApiUsage")
  private static void registerAllRoots(
      CommittableView committable, Map<URI, List<EObject>> rootsByUri) {
    for (Map.Entry<URI, List<EObject>> entry : rootsByUri.entrySet()) {
      List<EObject> roots = entry.getValue();
      committable.registerRoot(roots.getFirst(), entry.getKey());
      Resource resource = roots.getFirst().eResource();
      for (int i = 1; i < roots.size(); i++) {
        resource.getContents().add(roots.get(i));
      }
    }
  }

  /**
   * Commits every root of the given snapshot into the V-SUM. References that leave their own
   * resource are detached before the roots are registered and put back before the changes are
   * committed. Whether the changes are recorded or derived follows from the metamodels involved.
   *
   * @param vsum the V-SUM to insert into
   * @param snapshot the detached models to insert, which may be empty
   * @throws IllegalStateException if the view cannot be closed afterwards
   */
  public void replayInto(InternalVirtualModel vsum, ModelSnapshot snapshot) {
    Map<URI, List<EObject>> rootsByUri = snapshot.rootsByUri();
    if (rootsByUri.isEmpty()) {
      return;
    }

    boolean recordLiveChanges = adapters.anyRootRequiresChangeRecording(snapshot.allRoots());
    DetachedReferences detached = CrossResourceReferences.detach(rootsByUri);
    try (View view = Vsums.openEmptyView(vsum, "migration-replay")) {
      CommittableView committable =
          recordLiveChanges ? view.withChangeRecordingTrait() : view.withChangeDerivingTrait();
      registerAllRoots(committable, rootsByUri);
      detached.reattach();
      committable.commitChanges();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Closing the replay view failed", e);
    }
  }
}
