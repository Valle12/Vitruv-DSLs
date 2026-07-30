package tools.vitruv.dsls.reactions.tests

import allElementTypes.AllElementTypesFactory
import allElementTypes.AllElementTypesPackage
import com.google.inject.Inject
import java.util.LinkedHashMap
import java.util.List
import java.util.Map
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.EReference
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.impl.ResourceImpl
import org.eclipse.xtext.testing.InjectWith
import org.eclipse.xtext.testing.extensions.InjectionExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.^extension.ExtendWith
import tools.vitruv.change.atomic.EChange
import tools.vitruv.change.atomic.TypeInferringAtomicEChangeFactory
import tools.vitruv.change.propagation.ChangePropagationSpecification
import tools.vitruv.change.propagation.ConsistencyRuleId
import tools.vitruv.change.propagation.ConsistencyRuleTrigger
import tools.vitruv.change.propagation.ConsistencyRuleTriggerMatcher
import tools.vitruv.dsls.reactions.runtime.reactions.AbstractReactionsChangePropagationSpecification
import tools.vitruv.dsls.reactions.runtime.reactions.Reaction

import static java.nio.file.Files.createTempDirectory
import static org.hamcrest.CoreMatchers.*
import static org.hamcrest.MatcherAssert.assertThat

@ExtendWith(InjectionExtension)
@InjectWith(ReactionsLanguageInjectorProvider)
class ConsistencyRuleTriggerAgreementTest {
	static val SEGMENT_NAME = 'triggerKinds'
	static val MATCH_METHOD_NAME = 'isCurrentChangeMatchingTrigger'
	static val REACTIONS_FIELD_NAME = 'reactions'
	static val REACTION_CLASS_SUFFIX = 'Reaction'
	static val changeFactory = TypeInferringAtomicEChangeFactory.instance

	static val EVERY_TRIGGER_KIND = #{
		'EChange',
		'CreateEObject',
		'DeleteEObject',
		'InsertRootEObject',
		'RemoveRootEObject',
		'InsertEReference',
		'RemoveEReference',
		'ReplaceSingleValuedEReference',
		'InsertEAttributeValue',
		'RemoveEAttributeValue',
		'ReplaceSingleValuedEAttribute'
	}

	@Inject TestReactionsCompiler.Factory compilerFactory

	private def ChangePropagationSpecification compiledSpecification() {
		val compilationDir = createTempDirectory("reactions-trigger-agreement")
		val compiler = compilerFactory.createCompiler [
			reactionsOwner = this
			compilationProjectDir = compilationDir
			reactions = #['/tools/vitruv/dsls/reactions/tests/TriggerKinds.reactions']
			changePropagationSegments = #[SEGMENT_NAME]
		]

		val specifications = compiler.changePropagationSpecifications.toList
		assertThat("exactly one specification must be generated for segment " + SEGMENT_NAME, specifications.size, is(1))
		return specifications.head
	}

	private def List<Reaction> registeredReactions(ChangePropagationSpecification specification) {
		val reactionsField = AbstractReactionsChangePropagationSpecification.getDeclaredField(REACTIONS_FIELD_NAME)
		reactionsField.accessible = true
		return reactionsField.get(specification) as List<Reaction>
	}

	private def Map<ConsistencyRuleId, Reaction> reactionsByRuleId(ChangePropagationSpecification specification) {
		val reactionsBySimpleName = specification.registeredReactions.toMap[class.simpleName]
		val reactionsByRuleId = new LinkedHashMap<ConsistencyRuleId, Reaction>()
		for (ruleId : specification.consistencyRuleTriggers.keySet) {
			val reactionName = ruleId.value.substring(ruleId.value.lastIndexOf(':') + 1) + REACTION_CLASS_SUFFIX
			val reaction = reactionsBySimpleName.get(reactionName)
			assertThat("no generated reaction class " + reactionName + " for rule " + ruleId, reaction !== null, is(true))
			reactionsByRuleId.put(ruleId, reaction)
		}

		return reactionsByRuleId
	}

	private def boolean firesAccordingToGeneratedCheck(Reaction reaction, EChange<EObject> change) {
		val matchMethod = reaction.class.getMethod(MATCH_METHOD_NAME, EChange)
		return matchMethod.invoke(reaction, change) as Boolean
	}

	private def Map<String, EChange<EObject>> changesUnderTest() {
		val root = AllElementTypesFactory.eINSTANCE.createRoot
		val otherRoot = AllElementTypesFactory.eINSTANCE.createRoot
		val nonRoot = AllElementTypesFactory.eINSTANCE.createNonRoot
		val containerHelper = AllElementTypesFactory.eINSTANCE.createNonRootObjectContainerHelper
		val Resource resource = new ResourceImpl(URI.createURI("test:/model.allElementTypes"))
		resource.contents += root

		val containment = AllElementTypesPackage.Literals.
			NON_ROOT_OBJECT_CONTAINER_HELPER__NON_ROOT_OBJECTS_CONTAINMENT as EReference
		val multiContainment = AllElementTypesPackage.Literals.ROOT__MULTI_VALUED_CONTAINMENT_EREFERENCE as EReference
		val singleContainment = AllElementTypesPackage.Literals.ROOT__SINGLE_VALUED_CONTAINMENT_EREFERENCE as EReference
		val singleAttribute = AllElementTypesPackage.Literals.ROOT__SINGLE_VALUED_EATTRIBUTE
		val multiAttribute = AllElementTypesPackage.Literals.ROOT__MULTI_VALUED_EATTRIBUTE

		val changes = new LinkedHashMap<String, EChange<EObject>>()
		changes.put("insertIntoContainerHelper",
			changeFactory.createInsertReferenceChange(containerHelper, containment, nonRoot, 0))
		changes.put("insertWrongValueTypeIntoContainerHelper",
			changeFactory.createInsertReferenceChange(containerHelper, containment, otherRoot, 0))
		changes.put("insertIntoOtherFeature",
			changeFactory.createInsertReferenceChange(root, multiContainment, nonRoot, 0))
		changes.put("insertAsRoot", changeFactory.createInsertRootChange(root, resource, 0))
		changes.put("removeAsRoot", changeFactory.createRemoveRootChange(root, resource, 0))
		changes.put("removeFromList",
			changeFactory.createRemoveReferenceChange(root, multiContainment, nonRoot, 0))
		changes.put("removeWrongValueTypeFromList",
			changeFactory.createRemoveReferenceChange(root, multiContainment, otherRoot, 0))
		changes.put("replaceElement",
			changeFactory.createReplaceSingleReferenceChange(root, singleContainment, null, nonRoot))
		changes.put("replaceElementToDefault",
			changeFactory.createReplaceSingleReferenceChange(root, singleContainment, nonRoot, null))
		changes.put("replaceAttribute",
			changeFactory.createReplaceSingleAttributeChange(root, singleAttribute, null, 42))
		changes.put("replaceAttributeToDefault",
			changeFactory.createReplaceSingleAttributeChange(root, singleAttribute, 42, null))
		changes.put("insertAttribute", changeFactory.createInsertAttributeChange(root, multiAttribute, 0, 42))
		changes.put("removeAttribute", changeFactory.createRemoveAttributeChange(root, multiAttribute, 0, 42))
		changes.put("createElement", changeFactory.createCreateEObjectChange(nonRoot))
		changes.put("createOtherElement", changeFactory.createCreateEObjectChange(root))
		changes.put("deleteElement", changeFactory.createDeleteEObjectChange(nonRoot))
		return changes
	}

	@Test
	def void testDescriptorSelectsExactlyWhatTheGeneratedCheckAccepts() {
		val specification = compiledSpecification
		val triggers = specification.consistencyRuleTriggers
		val reactions = specification.reactionsByRuleId
		val changes = changesUnderTest

		assertThat("every reaction of the segment must expose a trigger descriptor",
			triggers.size, is(reactions.size))
		assertThat("the fixture must exercise every trigger kind the grammar can express",
			triggers.values.map[changeType].toSet, is(EVERY_TRIGGER_KIND))
		for (ruleId : triggers.keySet) {
			val matcher = ConsistencyRuleTriggerMatcher.of(triggers.get(ruleId))
			assertThat("no matcher for " + ruleId + " with trigger " + triggers.get(ruleId), matcher.present, is(true))
			for (change : changes.entrySet) {
				val expected = reactions.get(ruleId).firesAccordingToGeneratedCheck(change.value)
				assertThat('''«ruleId.value» disagrees on «change.key»'''.toString,
					matcher.get.matches(change.value), is(expected))
			}
		}
	}
}
