package tools.vitruv.dsls.reactions.migration.testspecs;

import java.util.Map;
import org.eclipse.emf.ecore.EObject;
import tools.vitruv.change.atomic.EChange;
import tools.vitruv.change.composite.MetamodelDescriptor;
import tools.vitruv.change.correspondence.Correspondence;
import tools.vitruv.change.correspondence.view.EditableCorrespondenceModelView;
import tools.vitruv.change.interaction.UserInteractor;
import tools.vitruv.change.propagation.ChangePropagationObserver;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.change.propagation.ConsistencyRuleId;
import tools.vitruv.change.propagation.ConsistencyRuleTrigger;
import tools.vitruv.change.utils.ResourceAccess;

public final class RuleAwareSpecification implements ChangePropagationSpecification {
  private final ChangePropagationSpecification delegate;
  private final Map<ConsistencyRuleId, String> ruleHashes;
  private final Map<ConsistencyRuleId, ConsistencyRuleTrigger> ruleTriggers;

  public RuleAwareSpecification(
      ChangePropagationSpecification delegate,
      Map<ConsistencyRuleId, String> ruleHashes,
      Map<ConsistencyRuleId, ConsistencyRuleTrigger> ruleTriggers) {
    this.delegate = delegate;
    this.ruleHashes = ruleHashes;
    this.ruleTriggers = ruleTriggers;
  }

  @Override
  public Map<ConsistencyRuleId, String> getConsistencyRuleHashes() {
    return ruleHashes;
  }

  @Override
  public Map<ConsistencyRuleId, ConsistencyRuleTrigger> getConsistencyRuleTriggers() {
    return ruleTriggers;
  }

  @Override
  public void setUserInteractor(UserInteractor userInteractor) {
    delegate.setUserInteractor(userInteractor);
  }

  @Override
  public MetamodelDescriptor getSourceMetamodelDescriptor() {
    return delegate.getSourceMetamodelDescriptor();
  }

  @Override
  public MetamodelDescriptor getTargetMetamodelDescriptor() {
    return delegate.getTargetMetamodelDescriptor();
  }

  @Override
  public boolean doesHandleChange(
      EChange<EObject> change,
      EditableCorrespondenceModelView<Correspondence> correspondenceModel) {
    return delegate.doesHandleChange(change, correspondenceModel);
  }

  @Override
  public void propagateChange(
      EChange<EObject> change,
      EditableCorrespondenceModelView<Correspondence> correspondenceModel,
      ResourceAccess resourceAccess) {
    delegate.propagateChange(change, correspondenceModel, resourceAccess);
  }

  @Override
  public void notifyObjectCreated(EObject createdObject) {
    delegate.notifyObjectCreated(createdObject);
  }

  @Override
  public void notifyChangePropagationStarted(
      ChangePropagationSpecification specification, EChange<EObject> change) {
    delegate.notifyChangePropagationStarted(specification, change);
  }

  @Override
  public void notifyChangePropagationStopped(
      ChangePropagationSpecification specification, EChange<EObject> change) {
    delegate.notifyChangePropagationStopped(specification, change);
  }

  @Override
  public void registerObserver(ChangePropagationObserver observer) {
    delegate.registerObserver(observer);
  }

  @Override
  public void deregisterObserver(ChangePropagationObserver observer) {
    delegate.deregisterObserver(observer);
  }
}
