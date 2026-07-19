package tools.vitruv.dsls.reactions.migration.vsum;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import tools.vitruv.change.propagation.ConsistencyRuleId;
import tools.vitruv.change.propagation.RuleHashRegistry;
import tools.vitruv.change.utils.ResourceAccess;
import tools.vitruv.framework.vsum.helper.VsumFileSystemLayout;

/**
 * Reads the registry of consistency-rule hashes (rule id to semantic hash of the rule's
 * implementation) that change propagation persists in a VSUM's consistency metadata.
 */
public final class PersistedRuleHashes {
  private PersistedRuleHashes() {}

  /** Loads the rule hashes persisted in the given VSUM project folder, empty if none were persisted. */
  public static Map<ConsistencyRuleId, String> load(Path vsumProjectFolder) {
    VsumFileSystemLayout layout = new VsumFileSystemLayout(vsumProjectFolder);
    try {
      layout.prepare();
      RuleHashRegistry registry = new RuleHashRegistry();
      registry.loadFromUri(RuleHashRegistry.getRegistryUri(new LayoutResourceAccess(layout)));
      return registry.getRuleHashes();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to load rule hashes from " + vsumProjectFolder, e);
    }
  }

  /** Adapts the layout to {@link ResourceAccess}, so the registry URI matches the one persisted to. */
  private record LayoutResourceAccess(VsumFileSystemLayout layout) implements ResourceAccess {
    @Override
    public URI getMetadataModelURI(String... metadataKey) {
      return layout.getConsistencyMetadataModelURI(metadataKey);
    }

    @Override
    public Resource getModelResource(URI uri) {
      throw new UnsupportedOperationException("only metadata URI resolution is supported");
    }

    @Override
    public void persistAsRoot(EObject rootObject, URI uri) {
      throw new UnsupportedOperationException("only metadata URI resolution is supported");
    }
  }
}
