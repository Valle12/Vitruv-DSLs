package tools.vitruv.dsls.reactions.ide

import java.util.List
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.xtext.util.CancelIndicator
import org.eclipse.xtext.validation.CheckMode
import org.eclipse.xtext.xbase.annotations.validation.DerivedStateAwareResourceValidator
import org.eclipse.xtext.validation.Issue

/**
 * Resource validator used inside the reactions language server.
 *
 * Needed to override editors behavior and not error out on existing feature annotations
 *
 * This is bound in {@link ReactionsLanguageIdeModule}.
 */
class EditorResourceValidator extends DerivedStateAwareResourceValidator {

	override List<Issue> validate(Resource resource, CheckMode mode, CancelIndicator cancelIndicator) {
		val effectiveMode = if (mode === CheckMode.ALL) CheckMode.NORMAL_AND_FAST else mode
		super.validate(resource, effectiveMode, cancelIndicator)
	}

}
