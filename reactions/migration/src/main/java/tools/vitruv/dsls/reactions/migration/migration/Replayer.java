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

@RequiredArgsConstructor
public class Replayer {
  private final AdapterRegistry adapters;

  @SuppressWarnings("UnstableApiUsage")
  private static void registerAllRoots(
      CommittableView committable, Map<URI, List<EObject>> rootsByUri) {
    for (Map.Entry<URI, List<EObject>> entry : rootsByUri.entrySet()) {
      List<EObject> roots = entry.getValue();
      committable.registerRoot(roots.get(0), entry.getKey());
      Resource resource = roots.get(0).eResource();
      for (int i = 1; i < roots.size(); i++) {
        resource.getContents().add(roots.get(i));
      }
    }
  }

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
