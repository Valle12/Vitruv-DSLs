package tools.vitruv.dsls.reactions.codegen.helper

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HashMap
import java.util.HexFormat
import java.util.LinkedHashSet
import java.util.Map
import java.util.Set
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.xtext.common.types.JvmOperation
import org.eclipse.xtext.xbase.XAbstractFeatureCall
import org.eclipse.xtext.xbase.jvmmodel.IJvmModelAssociations
import tools.vitruv.dsls.reactions.language.toplevelelements.Reaction
import tools.vitruv.dsls.reactions.language.toplevelelements.ReactionsSegment
import tools.vitruv.dsls.reactions.language.toplevelelements.Routine

import static extension tools.vitruv.dsls.reactions.codegen.helper.ReactionsImportsHelper.getIncludedRoutines
import static extension tools.vitruv.dsls.reactions.util.ReactionsLanguageUtil.getFullyQualifiedName
import static extension tools.vitruv.dsls.reactions.util.ReactionsLanguageUtil.getQualifiedName

/**
 * Computes the semantic hash of a reaction: a SHA-256 digest over the {@link NormalizedAstSerializer normalized
 * serialization} of the reaction and all (transitively) called routines, resolved to their overrides in the root
 * segment. Must only be used at code-generation time, as it relies on Xbase type resolution.
 */
class ConsistencyRuleHashCalculator {
	static val String REACTION_SECTION_PREFIX = "reaction:"
	static val String ROUTINE_SECTION_PREFIX = "routine:"

	val ReactionsSegment rootReactionsSegment
	val IJvmModelAssociations jvmModelAssociations
	val NormalizedAstSerializer serializer = new NormalizedAstSerializer()
	val Map<Routine, String> serializedRoutines = new HashMap()
	var Map<Routine, Routine> effectiveRoutines = null

	new(ReactionsSegment rootReactionsSegment, IJvmModelAssociations jvmModelAssociations) {
		this.rootReactionsSegment = rootReactionsSegment
		this.jvmModelAssociations = jvmModelAssociations
	}

	def String computeRuleHash(Reaction reaction) {
		val hashInput = new StringBuilder()
		hashInput.append(REACTION_SECTION_PREFIX).append(reaction.qualifiedName).append("\n")
		hashInput.append(serializer.serialize(reaction))
		for (routine : reaction.collectTransitivelyCalledRoutines.sortBy[qualifiedName]) {
			hashInput.append(ROUTINE_SECTION_PREFIX).append(routine.qualifiedName).append("\n")
			hashInput.append(serializedRoutines.computeIfAbsent(routine)[serializer.serialize(it)])
		}

		return sha256Hex(hashInput.toString())
	}

	private def Set<Routine> collectTransitivelyCalledRoutines(Reaction reaction) {
		val collectedRoutines = new LinkedHashSet<Routine>()
		val callCode = reaction.callRoutine?.code
		if (callCode !== null) {
			collectCalledRoutines(callCode, collectedRoutines)
		}

		return collectedRoutines
	}

	private def void collectCalledRoutines(EObject elementTree, Set<Routine> collectedRoutines) {
		visitElement(elementTree, collectedRoutines)
		val containedElements = EcoreUtil.getAllContents(elementTree, true)
		while (containedElements.hasNext) {
			visitElement(containedElements.next, collectedRoutines)
		}
	}

	private def void visitElement(EObject element, Set<Routine> collectedRoutines) {
		val feature = if (element instanceof XAbstractFeatureCall) element.feature
		if (!(feature instanceof JvmOperation) || feature.eIsProxy) {
			return
		}

		val sourceElement = jvmModelAssociations.getPrimarySourceElement(feature)
		val calledRoutine = if (sourceElement instanceof Routine) sourceElement.effectiveRoutine
		if (calledRoutine !== null && collectedRoutines.add(calledRoutine)) {
			collectCalledRoutines(calledRoutine, collectedRoutines)
		}
	}

	/** Resolves a routine to its override in the root segment's import hierarchy, or itself if not overridden. */
	private def Routine effectiveRoutine(Routine routine) {
		if (effectiveRoutines === null) {
			effectiveRoutines = buildEffectiveRoutinesMap()
		}

		return effectiveRoutines.getOrDefault(routine, routine)
	}

	private def Map<Routine, Routine> buildEffectiveRoutinesMap() {
		val originalRoutines = rootReactionsSegment.getIncludedRoutines(true, false)
		val effectiveRoutinesByFullyQualifiedName = new HashMap<String, Routine>()
		for (entry : rootReactionsSegment.getIncludedRoutines(true, true).entrySet) {
			effectiveRoutinesByFullyQualifiedName.put(entry.key.getFullyQualifiedName(entry.value), entry.key)
		}

		val originalToEffectiveRoutine = new HashMap<Routine, Routine>()
		for (entry : originalRoutines.entrySet) {
			val effectiveRoutine = effectiveRoutinesByFullyQualifiedName.get(entry.key.getFullyQualifiedName(entry.value))
			if (effectiveRoutine !== null) {
				originalToEffectiveRoutine.put(entry.key, effectiveRoutine)
			}
		}

		return originalToEffectiveRoutine
	}

	private def String sha256Hex(String hashInput) {
		val digest = MessageDigest.getInstance("SHA-256").digest(hashInput.getBytes(StandardCharsets.UTF_8))
		return HexFormat.of().formatHex(digest)
	}
}
