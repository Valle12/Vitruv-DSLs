package tools.vitruv.dsls.reactions.tests

import allElementTypes.AllElementTypesPackage
import com.google.inject.Inject
import com.google.inject.Provider
import java.util.LinkedHashMap
import java.util.Map
import java.util.regex.Pattern
import org.eclipse.xtext.common.types.JvmGenericType
import org.eclipse.xtext.common.types.JvmOperation
import org.eclipse.xtext.generator.InMemoryFileSystemAccess
import org.eclipse.xtext.resource.XtextResourceSet
import org.eclipse.xtext.testing.InjectWith
import org.eclipse.xtext.testing.extensions.InjectionExtension
import org.eclipse.xtext.testing.util.ParseHelper
import org.eclipse.xtext.xbase.XbaseFactory
import org.eclipse.xtext.xbase.lib.InputOutput
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.^extension.ExtendWith
import tools.vitruv.dsls.reactions.api.generator.IReactionsGenerator
import tools.vitruv.dsls.reactions.builder.FluentReactionsLanguageBuilder
import tools.vitruv.dsls.reactions.builder.TypeProvider
import tools.vitruv.dsls.reactions.language.toplevelelements.ReactionsFile

import static org.hamcrest.CoreMatchers.*
import static org.hamcrest.MatcherAssert.assertThat

@ExtendWith(InjectionExtension)
@InjectWith(ReactionsLanguageInjectorProvider)
class ConsistencyRuleTriggerTest {
	static val SEGMENT_NAME = 'triggerBasic'
	static val NS_URI = 'http://tools.vitruv.change.testutils.metamodels.allElementTypes'
	static val CHANGE_PROPAGATION_SPEC_NAME_SUFFIX = 'ChangePropagationSpecification'
	static val ECORE_URI = 'http://www.eclipse.org/emf/2002/Ecore'
	static val TRIGGER_ENTRY_PATTERN = Pattern.compile(
		'Map\\.entry\\(new (?:[\\w.]+\\.)?ConsistencyRuleId\\("([^"]+)"\\),\\s*new (?:[\\w.]+\\.)?ConsistencyRuleTrigger\\(\\s*"([^"]*)", "([^"]*)", "([^"]*)", "([^"]*)",\\s*(?:[\\w.]+\\.)?ValueSlot\\.(\\w+)\\)\\)')

	@Inject ParseHelper<ReactionsFile> parseHelper
	@Inject Provider<IReactionsGenerator> generatorProvider
	@Inject Provider<XtextResourceSet> resourceSetProvider
	@Inject Provider<InMemoryFileSystemAccess> fsaProvider

	private def CharSequence allTriggerKindsSource() '''
		import "«NS_URI»" as minimal

		reactions: «SEGMENT_NAME»
		in reaction to changes in minimal
		execute actions in minimal

		reaction InsertedInList {
			after element minimal::NonRoot inserted in minimal::NonRootObjectContainerHelper[nonRootObjectsContainment]
			call noop()
		}

		reaction InsertedAsRoot {
			after element minimal::Root inserted as root
			call noop()
		}

		reaction AttributeReplaced {
			after attribute replaced at minimal::Root[singleValuedEAttribute]
			call noop()
		}

		reaction ElementReplaced {
			after element minimal::NonRoot replaced at minimal::Root[singleValuedContainmentEReference]
			call noop()
		}

		reaction RemovedFromList {
			after element minimal::NonRoot removed from minimal::Root[multiValuedContainmentEReference]
			call noop()
		}

		reaction Created {
			after element minimal::NonRoot created
			call noop()
		}

		reaction Deleted {
			after element minimal::NonRoot deleted
			call noop()
		}

		routine noop() {
			update {
				val ignore = ""
			}
		}
	'''

	private def Map<String, String> generateAndExtractTriggers(CharSequence source, String segmentName) {
		val resourceSet = resourceSetProvider.get()
		val parsed = parseHelper.parse(source, resourceSet)
		assertThat("test source could not be parsed", parsed !== null, is(true))
		val generator = generatorProvider.get()
		generator.addReactionsFiles(resourceSet)
		val fsa = fsaProvider.get() => [
			currentSource = "src"
			outputPath = "src-gen"
		]

		generator.generate(fsa)
		return fsa.extractTriggers(segmentName)
	}

	private def Map<String, String> extractTriggers(InMemoryFileSystemAccess fsa, String segmentName) {
		val specificationFile = fsa.allFiles.entrySet.findFirst [
			key.endsWith(segmentName + '/' + segmentName.toFirstUpper + CHANGE_PROPAGATION_SPEC_NAME_SUFFIX + '.java')
		]

		assertThat("no change propagation specification was generated for segment " + segmentName,
			specificationFile !== null, is(true))
		val matcher = TRIGGER_ENTRY_PATTERN.matcher(specificationFile.value.toString)
		val triggers = new LinkedHashMap<String, String>()
		while (matcher.find) {
			triggers.put(matcher.group(1),
				#[matcher.group(2), matcher.group(3), matcher.group(4), matcher.group(5), matcher.group(6)].join(';'))
		}

		return triggers
	}

	@Test
	def void testAllTriggerKindsProduceDescriptors() {
		val triggers = generateAndExtractTriggers(allTriggerKindsSource, SEGMENT_NAME)
		assertThat(triggers, is(Map.of(
			SEGMENT_NAME + '::InsertedInList',
			'''InsertEReference;«NS_URI»#NonRootObjectContainerHelper;nonRootObjectsContainment;«NS_URI»#NonRoot;NEW_VALUE'''.toString,
			SEGMENT_NAME + '::InsertedAsRoot',
			'''InsertRootEObject;;;«NS_URI»#Root;NEW_VALUE'''.toString,
			SEGMENT_NAME + '::AttributeReplaced',
			'''ReplaceSingleValuedEAttribute;«NS_URI»#Root;singleValuedEAttribute;«ECORE_URI»#EIntegerObject;REPLACED_VALUE'''.toString,
			SEGMENT_NAME + '::ElementReplaced',
			'''ReplaceSingleValuedEReference;«NS_URI»#Root;singleValuedContainmentEReference;«NS_URI»#NonRoot;REPLACED_VALUE'''.toString,
			SEGMENT_NAME + '::RemovedFromList',
			'''RemoveEReference;«NS_URI»#Root;multiValuedContainmentEReference;«NS_URI»#NonRoot;OLD_VALUE'''.toString,
			SEGMENT_NAME + '::Created',
			'''CreateEObject;«NS_URI»#NonRoot;;;NONE'''.toString,
			SEGMENT_NAME + '::Deleted',
			'''DeleteEObject;«NS_URI»#NonRoot;;;NONE'''.toString)))
	}

	@Test
	def void testTriggersStableAcrossGenerations() {
		val firstTriggers = generateAndExtractTriggers(allTriggerKindsSource, SEGMENT_NAME)
		val secondTriggers = generateAndExtractTriggers(allTriggerKindsSource, SEGMENT_NAME)
		assertThat(secondTriggers, is(firstTriggers))
	}

	private def CharSequence overrideReactionSource() '''
		import "«NS_URI»" as minimal

		reactions: base
		in reaction to changes in minimal
		execute actions in minimal

		reaction ReactionA {
			after element minimal::NonRoot inserted in minimal::NonRootObjectContainerHelper[nonRootObjectsContainment]
			call noop()
		}

		routine noop() {
			update {
				val ignore = ""
			}
		}

		reactions: ext
		in reaction to changes in minimal
		execute actions in minimal
		import base

		reaction base::ReactionA {
			after attribute replaced at minimal::Root[singleValuedEAttribute]
			call extNoop()
		}

		routine extNoop() {
			update {
				val ignore = ""
			}
		}
	'''

	@Test
	def void testOverrideReactionExposesOverridingTriggerUnderOverriddenId() {
		val resourceSet = resourceSetProvider.get()
		parseHelper.parse(overrideReactionSource, resourceSet)
		val generator = generatorProvider.get()
		generator.addReactionsFiles(resourceSet)
		val fsa = fsaProvider.get() => [
			currentSource = "src"
			outputPath = "src-gen"
		]
		generator.generate(fsa)

		val baseTriggers = fsa.extractTriggers('base')
		val extTriggers = fsa.extractTriggers('ext')
		assertThat(baseTriggers.keySet, is(#{'base::ReactionA'}))
		assertThat(extTriggers.keySet, is(#{'base::ReactionA'}))
		assertThat(baseTriggers.get('base::ReactionA'),
			is('''InsertEReference;«NS_URI»#NonRootObjectContainerHelper;nonRootObjectsContainment;«NS_URI»#NonRoot;NEW_VALUE'''.toString))
		assertThat(extTriggers.get('base::ReactionA'),
			is('''ReplaceSingleValuedEAttribute;«NS_URI»#Root;singleValuedEAttribute;«ECORE_URI»#EIntegerObject;REPLACED_VALUE'''.toString))
	}

	@Test
	def void testAnyChangeTriggerProducesWildcardDescriptor() {
		val create = new FluentReactionsLanguageBuilder()
		val fileBuilder = create.reactionsFile('builderTriggerTest')
		fileBuilder += create.reactionsSegment('builderTriggerTest').inReactionToChangesIn(
			AllElementTypesPackage.eINSTANCE).executeActionsIn(AllElementTypesPackage.eINSTANCE) +=
			create.reaction('TestReaction').afterAnyChange.call [
				update [
					execute[createPrintlnStatement]
				]
			]

		val generator = generatorProvider.get()
		generator.useResourceSet(resourceSetProvider.get())
		generator.addReactionsFile(fileBuilder)
		val fsa = fsaProvider.get() => [
			currentSource = "src"
			outputPath = "src-gen"
		]
		generator.generate(fsa)

		val triggers = fsa.extractTriggers('builderTriggerTest')
		assertThat(triggers, is(Map.of('builderTriggerTest::TestReaction', 'EChange;;;;NONE')))
	}

	private def createPrintlnStatement(TypeProvider typeProvider) {
		XbaseFactory.eINSTANCE.createXFeatureCall => [
			val jvmType = typeProvider.findTypeByName(InputOutput.name) as JvmGenericType
			feature = jvmType.members.filter(JvmOperation).filter[it.simpleName == 'println'].head
			explicitOperationCall = true
			it.featureCallArguments += XbaseFactory.eINSTANCE.createXStringLiteral() => [
				it.value = 'That\'s it';
			]
		]
	}
}
