package tools.vitruv.dsls.reactions.codegen.helper

import java.nio.charset.StandardCharsets
import org.eclipse.emf.ecore.EAttribute
import org.eclipse.emf.ecore.EClassifier
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.EPackage
import org.eclipse.emf.ecore.EReference
import org.eclipse.emf.ecore.EStructuralFeature
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.xtext.common.types.JvmIdentifiableElement
import tools.vitruv.dsls.common.elements.MetamodelImport
import tools.vitruv.dsls.reactions.language.toplevelelements.Reaction
import tools.vitruv.dsls.reactions.language.toplevelelements.ReactionsSegment
import tools.vitruv.dsls.reactions.language.toplevelelements.Routine

import static extension tools.vitruv.dsls.reactions.util.ReactionsLanguageUtil.getQualifiedName

/**
 * Serializes the normalized (no comments, no formatting) AST for a reaction with the called (also transitively)
 * routines to a string, which can be used to create the semantic hash
 */
class NormalizedAstSerializer {
	static val String UNRESOLVED_TOKEN = "<unresolved>"
	static val String NULL_TOKEN = "<null>"
	static val String DOCUMENTATION_FEATURE_NAME = "documentation"

	def String serialize(EObject root) {
		val builder = new StringBuilder()
		serializeElement(root, builder)
		return builder.toString()
	}

	private def void serializeElement(EObject element, StringBuilder builder) {
		builder.append("(").append(element.eClass.EPackage.nsURI).append("#").append(element.eClass.name).append("\n")
		for (feature : element.eClass.EAllStructuralFeatures.sortBy[name]) {
			if (!feature.isSkipped && element.eIsSet(feature)) {
				builder.append(feature.name).append("=").append("\n")
				switch feature {
					EAttribute:
						serializeAttributeValues(element, feature, builder)
					EReference case feature.containment:
						element.valuesOf(feature).forEach[serializeElement(it as EObject, builder)]
					EReference:
						element.valuesOf(feature).forEach[appendToken(identityOf(it as EObject), builder)]
				}
			}
		}

		builder.append(")").append("\n")
	}

	private def boolean isSkipped(EStructuralFeature feature) {
		return feature.derived || feature.transient || feature.volatile ||
			feature.name == DOCUMENTATION_FEATURE_NAME
	}

	private def void serializeAttributeValues(EObject element, EAttribute attribute, StringBuilder builder) {
		for (value : element.valuesOf(attribute)) {
			if (value === null) {
				appendToken(NULL_TOKEN, builder)
			} else {
				appendToken(EcoreUtil.convertToString(attribute.EAttributeType, value), builder)
			}
		}
	}

	private def Iterable<?> valuesOf(EObject element, EStructuralFeature feature) {
		val value = element.eGet(feature)
		return if (feature.many) value as Iterable<?> else #[value]
	}

	/**
	 * Returns a stable identity for a cross-referenced element. The identity must not change when the target element's
	 * surroundings are reformatted or its containing resource is regenerated, but must differ for semantically
	 * different targets.
	 */
	private def String identityOf(EObject target) {
		if (target === null) return NULL_TOKEN
		if (target.eIsProxy) return UNRESOLVED_TOKEN
		return switch target {
			MetamodelImport: target.package?.nsURI ?: UNRESOLVED_TOKEN
			Reaction: target.qualifiedName
			Routine: target.qualifiedName
			ReactionsSegment: target.name
			JvmIdentifiableElement: target.identifier
			EPackage: target.nsURI
			EClassifier: target.EPackage?.nsURI + "#" + target.name
			EStructuralFeature: target.EContainingClass.EPackage?.nsURI + "#" + target.EContainingClass.name + "." +
				target.name
			default: target.eClass.name + "#" + target.nameAttributeValue
		}
	}

	private def String getNameAttributeValue(EObject target) {
		val nameFeature = target.eClass.getEStructuralFeature("name")
		if (nameFeature instanceof EAttribute) {
			val value = target.eGet(nameFeature)
			if (value !== null) {
				return value.toString()
			}
		}
		return "?"
	}

	private def void appendToken(String token, StringBuilder builder) {
		val text = token ?: NULL_TOKEN
		builder.append(text.getBytes(StandardCharsets.UTF_8).length).append(":").append(text).append("\n")
	}
}
