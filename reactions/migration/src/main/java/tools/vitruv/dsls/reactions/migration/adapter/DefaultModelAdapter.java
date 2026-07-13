package tools.vitruv.dsls.reactions.migration.adapter;

public class DefaultModelAdapter implements MetamodelAdapter {
  @Override
  public boolean handles(String nsUri) {
    return true;
  }
}
