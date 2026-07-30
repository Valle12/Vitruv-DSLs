package tools.vitruv.dsls.reactions.migration.vsum;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import tools.vitruv.change.propagation.ConsistencyRuleId;
import tools.vitruv.change.propagation.ConsistencyRuleTrigger;
import tools.vitruv.change.propagation.RuleHashRegistry;
import tools.vitruv.change.utils.ResourceAccess;
import tools.vitruv.framework.vsum.helper.VsumFileSystemLayout;

public record PersistedRules(
    Map<ConsistencyRuleId, String> hashes,
    Map<ConsistencyRuleId, ConsistencyRuleTrigger> triggers) {

  public static PersistedRules load(Path vsumProjectFolder) {
    VsumFileSystemLayout layout = new VsumFileSystemLayout(vsumProjectFolder);
    try {
      layout.prepare();
      RuleHashRegistry registry = new RuleHashRegistry();
      registry.loadFromUri(RuleHashRegistry.getRegistryUri(new LayoutResourceAccess(layout)));
      return new PersistedRules(registry.getRuleHashes(), registry.getRuleTriggers());
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to load persisted rules from " + vsumProjectFolder, e);
    }
  }

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
