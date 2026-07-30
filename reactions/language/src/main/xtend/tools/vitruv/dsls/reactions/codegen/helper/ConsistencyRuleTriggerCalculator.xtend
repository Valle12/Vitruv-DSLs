package tools.vitruv.dsls.reactions.codegen.helper

import org.eclipse.emf.ecore.EClassifier
import tools.vitruv.change.propagation.ConsistencyRuleTrigger
import tools.vitruv.change.propagation.ConsistencyRuleTrigger.ValueSlot
import tools.vitruv.dsls.reactions.codegen.changetyperepresentation.ChangeTypeRepresentation

final class ConsistencyRuleTriggerCalculator {
	private new() {
	}

	static def ConsistencyRuleTrigger computeTrigger(ChangeTypeRepresentation changeTypeRepresentation) {
		return new ConsistencyRuleTrigger(
			changeTypeRepresentation.changeType.simpleName,
			changeTypeRepresentation.affectedElementEClassifier.typeDescriptor,
			changeTypeRepresentation.affectedFeature?.name ?: "",
			changeTypeRepresentation.affectedValueEClassifier.typeDescriptor,
			changeTypeRepresentation.valueSlot
		)
	}

	private static def ValueSlot getValueSlot(ChangeTypeRepresentation changeTypeRepresentation) {
		if (changeTypeRepresentation.affectedValueEClassifier === null)
			ValueSlot.NONE
		else if (changeTypeRepresentation.hasOldValue && changeTypeRepresentation.hasNewValue)
			ValueSlot.REPLACED_VALUE
		else if (changeTypeRepresentation.hasNewValue)
			ValueSlot.NEW_VALUE
		else if (changeTypeRepresentation.hasOldValue)
			ValueSlot.OLD_VALUE
		else
			ValueSlot.NONE
	}

	private static def String getTypeDescriptor(EClassifier classifier) {
		if (classifier === null || classifier.EPackage === null)
			""
		else
			classifier.EPackage.nsURI + "#" + classifier.name
	}
}
