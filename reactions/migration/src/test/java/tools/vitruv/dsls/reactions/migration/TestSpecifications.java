package tools.vitruv.dsls.reactions.migration;

import org.eclipse.emf.ecore.EObject;
import tools.vitruv.change.atomic.EChange;
import tools.vitruv.change.composite.MetamodelDescriptor;
import tools.vitruv.change.correspondence.Correspondence;
import tools.vitruv.change.correspondence.view.EditableCorrespondenceModelView;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.change.propagation.impl.AbstractChangePropagationSpecification;
import tools.vitruv.change.utils.ResourceAccess;

public final class TestSpecifications {
  public static final String UML = "http://www.eclipse.org/uml2/5.0.0/UML";
  public static final String JAVA = "http://www.emftext.org/java";
  public static final String PCM = "http://palladiosimulator.org/PalladioComponentModel/5.2";
  public static final String FAMILIES = "http://families/1.0";
  public static final String PERSONS = "http://persons/1.0";

  private TestSpecifications() {}

  public static ChangePropagationSpecification spec(String sourceUri, String targetUri) {
    return new AbstractChangePropagationSpecification(
        MetamodelDescriptor.with(sourceUri), MetamodelDescriptor.with(targetUri)) {
      @Override
      public boolean doesHandleChange(
          EChange<EObject> change, EditableCorrespondenceModelView<Correspondence> view) {
        return false;
      }

      @Override
      public void propagateChange(
          EChange<EObject> change,
          EditableCorrespondenceModelView<Correspondence> view,
          ResourceAccess resourceAccess) {
        throw new UnsupportedOperationException(
            "planning only test specification does not handle any change");
      }
    };
  }
}
