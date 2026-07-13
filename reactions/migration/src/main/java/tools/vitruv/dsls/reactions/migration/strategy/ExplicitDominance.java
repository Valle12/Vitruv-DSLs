package tools.vitruv.dsls.reactions.migration.strategy;

import java.util.Optional;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;

public class ExplicitDominance implements DominanceStrategy {
  private final String token;

  public ExplicitDominance(String token) {
    this.token = token.toLowerCase();
  }

  @Override
  public Optional<MetamodelNode> selectDominant(DominanceContext context) {
    return context.presentNodes().stream().filter(this::matchesToken).findFirst();
  }

  private boolean matchesToken(MetamodelNode node) {
    return node.nsUris().stream().anyMatch(nsUri -> nsUri.toLowerCase().contains(token));
  }
}
