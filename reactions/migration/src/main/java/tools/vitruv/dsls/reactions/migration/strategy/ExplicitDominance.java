package tools.vitruv.dsls.reactions.migration.strategy;

import java.util.Optional;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;

/**
 * Chooses the dominant model the caller named, by looking for the given token in the namespace
 * URIs of the present metamodels.
 */
public class ExplicitDominance implements DominanceStrategy {
  private final String token;

  /**
   * Creates a strategy selecting the metamodel whose namespace URI contains the given token.
   *
   * @param token a metamodel token such as {@code uml} or {@code java}, matched without regard
   *     to case
   */
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
