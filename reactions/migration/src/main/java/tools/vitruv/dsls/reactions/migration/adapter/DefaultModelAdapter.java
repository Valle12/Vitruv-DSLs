package tools.vitruv.dsls.reactions.migration.adapter;

/**
 * The adapter every namespace falls back to, which leaves each hook of {@link MetamodelAdapter}
 * at its default and therefore treats the metamodel as plain EMF.
 */
public class DefaultModelAdapter implements MetamodelAdapter {
  @Override
  public boolean handles(String nsUri) {
    return true;
  }
}
