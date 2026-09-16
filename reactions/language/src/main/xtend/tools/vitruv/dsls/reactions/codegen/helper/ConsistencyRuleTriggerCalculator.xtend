package tools.vitruv.dsls.reactions.codegen.helper

import org.eclipse.emf.ecore.EClassifier
import tools.vitruv.change.propagation.ConsistencyRuleTrigger
import tools.vitruv.change.propagation.ConsistencyRuleTrigger.ValueSlot
import tools.vitruv.dsls.reactions.codegen.changetyperepresentation.ChangeTypeRepresentation

/**
 * Derives the trigger summary of a reaction from the change type it reacts to. The summary
 * names the change type, the affected metaclass, the affected feature, the type of the value
 * and the slot that value sits in, which is what a migration compares a change against.
 */
final class ConsistencyRuleTriggerCalculator {
	private new() {
	}

	/**
	 * Computes the trigger summary of the given change type.
	 *
	 * @param changeTypeRepresentation the change type a reaction reacts to
	 * @return the trigger summary, with metaclasses written as namespace URI and class name and
	 * 		an absent feature or value type written as the empty string
	 */
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
