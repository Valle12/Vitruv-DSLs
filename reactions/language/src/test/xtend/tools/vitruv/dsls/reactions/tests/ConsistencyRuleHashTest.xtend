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

import static java.nio.file.Files.createTempDirectory
import static org.hamcrest.CoreMatchers.*
import static org.hamcrest.MatcherAssert.assertThat

/**
 * Tests that the generated change propagation specifications expose a semantic hash per consistency rule (reaction)
 * and that the hash is only sensitive to semantic changes of the reaction and its transitively called routines.
 */
@ExtendWith(InjectionExtension)
@InjectWith(ReactionsLanguageInjectorProvider)
class ConsistencyRuleHashTest {
	static val SEGMENT_NAME = 'hashBasic'
	static val DEFAULT_TRIGGER = 'minimal::NonRoot inserted in minimal::NonRootObjectContainerHelper[nonRootObjectsContainment]'
	static val DEFAULT_STATEMENT = 'nonRootElement.id = nonRootElement.id'
	static val CHANGE_PROPAGATION_SPEC_NAME_SUFFIX = 'ChangePropagationSpecification'
	static val HEX_HASH_PATTERN = Pattern.compile('[0-9a-f]{64}')
	static val HASH_ENTRY_PATTERN = Pattern.
		compile('Map\\.entry\\(new (?:[\\w.]+\\.)?ConsistencyRuleId\\("([^"]+)"\\),\\s*"([0-9a-f]{64})"\\)')

	@Inject TestReactionsCompiler.Factory compilerFactory
	@Inject ParseHelper<ReactionsFile> parseHelper
	@Inject Provider<IReactionsGenerator> generatorProvider
	@Inject Provider<XtextResourceSet> resourceSetProvider
	@Inject Provider<InMemoryFileSystemAccess> fsaProvider

	private def CharSequence reactionsSource(String reactionATrigger, String routineAStatement,
		String routineBStatement, String routineCStatement) '''
		import "http://tools.vitruv.change.testutils.metamodels.allElementTypes" as minimal

		reactions: «SEGMENT_NAME»
		in reaction to changes in minimal
		execute actions in minimal

		reaction ReactionA {
			after element «reactionATrigger»
			call routineA(newValue)
		}

		reaction ReactionB {
			after element minimal::Root inserted as root
			call routineD(newValue)
		}

		routine routineA(minimal::NonRoot nonRootElement) {
			match {
				require absence of minimal::NonRoot corresponding to nonRootElement
			}
			create {
				val newNonRoot = new minimal::NonRoot
			}
			update {
				newNonRoot.id = nonRootElement.id
				«routineAStatement»
				routineB(nonRootElement)
			}
		}

		routine routineB(minimal::NonRoot nonRootElement) {
			update {
				«routineBStatement»
			}
		}

		routine routineC(minimal::NonRoot nonRootElement) {
			update {
				«routineCStatement»
			}
		}

		routine routineD(minimal::Root rootElement) {
			update {
				rootElement.id = rootElement.id
			}
		}
	'''

	private def CharSequence baseSource() {
		reactionsSource(DEFAULT_TRIGGER, DEFAULT_STATEMENT, DEFAULT_STATEMENT, DEFAULT_STATEMENT)
	}

	private def Map<String, String> generateAndExtractHashes(CharSequence source, String segmentName) {
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
		return fsa.extractHashes(segmentName)
	}

	private def Map<String, String> extractHashes(InMemoryFileSystemAccess fsa, String segmentName) {
		val specificationFile = fsa.allFiles.entrySet.findFirst [
			key.endsWith(segmentName + '/' + segmentName.toFirstUpper + CHANGE_PROPAGATION_SPEC_NAME_SUFFIX + '.java')
		]

		assertThat("no change propagation specification was generated for segment " + segmentName,
			specificationFile !== null, is(true))
		val matcher = HASH_ENTRY_PATTERN.matcher(specificationFile.value.toString)
		val hashes = new LinkedHashMap<String, String>()
		while (matcher.find) {
			hashes.put(matcher.group(1), matcher.group(2))
		}

		return hashes
	}

	@Test
	def void testCompiledSpecificationExposesRuleHashes() {
		val compilationDir = createTempDirectory("reactions-hash-compilation")
		val compiler = compilerFactory.createCompiler [
			reactionsOwner = this
			compilationProjectDir = compilationDir
			reactions = #['/tools/vitruv/dsls/reactions/tests/HashBasic.reactions']
			changePropagationSegments = #[SEGMENT_NAME]
		]

		val specifications = compiler.changePropagationSpecifications.toList
		assertThat(specifications.size, is(1))
		val ruleHashes = specifications.head.consistencyRuleHashes
		assertThat(ruleHashes.keySet.map[value].toSet,
			is(#{SEGMENT_NAME + '::ReactionA', SEGMENT_NAME + '::ReactionB'}))
		for (hash : ruleHashes.values) {
			assertThat("rule hash is not a SHA-256 hex string: " + hash, HEX_HASH_PATTERN.matcher(hash).matches, is(true))
		}
	}

	@Test
	def void testHashStableAcrossGenerations() {
		val firstHashes = generateAndExtractHashes(baseSource, SEGMENT_NAME)
		val secondHashes = generateAndExtractHashes(baseSource, SEGMENT_NAME)
		assertThat(firstHashes.size, is(2))
		assertThat(secondHashes, is(firstHashes))
	}

	@Test
	def void testCommentsAndFormattingDoNotChangeHash() {
		val commentedSource = '''
			import "http://tools.vitruv.change.testutils.metamodels.allElementTypes" as minimal


			reactions: «SEGMENT_NAME»
			in reaction to changes in minimal
			execute actions in minimal

			/* documentation of ReactionA */
			reaction ReactionA {
					after element «DEFAULT_TRIGGER»
				// a single line comment
					call routineA(newValue)
			}

			reaction ReactionB {
				after element minimal::Root inserted as root
				call routineD(newValue)
			}

			/* documentation of routineA */
			routine routineA(minimal::NonRoot nonRootElement) {
				match {
					require absence of minimal::NonRoot corresponding to nonRootElement
				}
				create {
					val newNonRoot = new minimal::NonRoot
				}
				update {
						newNonRoot.id = nonRootElement.id
					«DEFAULT_STATEMENT»

					// another comment
					routineB(nonRootElement)
				}
			}

			routine routineB(minimal::NonRoot nonRootElement) {
				update {
					«DEFAULT_STATEMENT»
				}
			}

			routine routineC(minimal::NonRoot nonRootElement) {
				update {
					«DEFAULT_STATEMENT»
				}
			}

			routine routineD(minimal::Root rootElement) {
				update {
					rootElement.id = rootElement.id
				}
			}
		'''
		assertThat(generateAndExtractHashes(commentedSource, SEGMENT_NAME),
			is(generateAndExtractHashes(baseSource, SEGMENT_NAME)))
	}

	@Test
	def void testChangedCalledRoutineChangesHash() {
		val baseHashes = generateAndExtractHashes(baseSource, SEGMENT_NAME)
		val changedHashes = generateAndExtractHashes(
			reactionsSource(DEFAULT_TRIGGER, 'nonRootElement.id = "changed"', DEFAULT_STATEMENT, DEFAULT_STATEMENT),
			SEGMENT_NAME)
		assertThat(changedHashes.get(SEGMENT_NAME + '::ReactionA'),
			is(not(baseHashes.get(SEGMENT_NAME + '::ReactionA'))))
		assertThat(changedHashes.get(SEGMENT_NAME + '::ReactionB'), is(baseHashes.get(SEGMENT_NAME + '::ReactionB')))
	}

	@Test
	def void testChangedTransitivelyCalledRoutineChangesHash() {
		val baseHashes = generateAndExtractHashes(baseSource, SEGMENT_NAME)
		val changedHashes = generateAndExtractHashes(
			reactionsSource(DEFAULT_TRIGGER, DEFAULT_STATEMENT, 'nonRootElement.id = "changed"', DEFAULT_STATEMENT),
			SEGMENT_NAME)
		assertThat(changedHashes.get(SEGMENT_NAME + '::ReactionA'),
			is(not(baseHashes.get(SEGMENT_NAME + '::ReactionA'))))
		assertThat(changedHashes.get(SEGMENT_NAME + '::ReactionB'), is(baseHashes.get(SEGMENT_NAME + '::ReactionB')))
	}

	@Test
	def void testChangedUncalledRoutineDoesNotChangeHash() {
		val baseHashes = generateAndExtractHashes(baseSource, SEGMENT_NAME)
		val changedHashes = generateAndExtractHashes(
			reactionsSource(DEFAULT_TRIGGER, DEFAULT_STATEMENT, DEFAULT_STATEMENT, 'nonRootElement.id = "changed"'),
			SEGMENT_NAME)
		assertThat(changedHashes, is(baseHashes))
	}

	@Test
	def void testChangedTriggerChangesHash() {
		val baseHashes = generateAndExtractHashes(baseSource, SEGMENT_NAME)
		val changedHashes = generateAndExtractHashes(
			reactionsSource(DEFAULT_TRIGGER + ' with newValue.id !== null', DEFAULT_STATEMENT, DEFAULT_STATEMENT,
				DEFAULT_STATEMENT), SEGMENT_NAME)
		assertThat(changedHashes.get(SEGMENT_NAME + '::ReactionA'),
			is(not(baseHashes.get(SEGMENT_NAME + '::ReactionA'))))
		assertThat(changedHashes.get(SEGMENT_NAME + '::ReactionB'), is(baseHashes.get(SEGMENT_NAME + '::ReactionB')))
	}

	private def CharSequence overrideReactionSource(boolean withOverrideReaction) '''
		import "http://tools.vitruv.change.testutils.metamodels.allElementTypes" as minimal

		reactions: base
		in reaction to changes in minimal
		execute actions in minimal

		reaction ReactionA {
			after element «DEFAULT_TRIGGER»
			call routineA(newValue)
		}

		routine routineA(minimal::NonRoot nonRootElement) {
			update {
				«DEFAULT_STATEMENT»
			}
		}

		reactions: ext
		in reaction to changes in minimal
		execute actions in minimal
		import base

		«IF withOverrideReaction»
			reaction base::ReactionA {
				after element «DEFAULT_TRIGGER»
				call extRoutine(newValue)
			}

			routine extRoutine(minimal::NonRoot nonRootElement) {
				update {
					nonRootElement.id = "overridden"
				}
			}
		«ENDIF»
	'''

	@Test
	def void testOverrideReactionKeepsOverriddenRuleId() {
		val resourceSet = resourceSetProvider.get()
		parseHelper.parse(overrideReactionSource(true), resourceSet)
		val generator = generatorProvider.get()
		generator.addReactionsFiles(resourceSet)
		val fsa = fsaProvider.get() => [
			currentSource = "src"
			outputPath = "src-gen"
		]
		generator.generate(fsa)

		val baseHashes = fsa.extractHashes('base')
		val extHashes = fsa.extractHashes('ext')
		// the override keeps the overridden rule's id, but has a different implementation and thus a different hash:
		assertThat(baseHashes.keySet, is(#{'base::ReactionA'}))
		assertThat(extHashes.keySet, is(#{'base::ReactionA'}))
		assertThat(extHashes.get('base::ReactionA'), is(not(baseHashes.get('base::ReactionA'))))
	}

	private def CharSequence overrideRoutineSource(boolean withOverrideRoutine) '''
		import "http://tools.vitruv.change.testutils.metamodels.allElementTypes" as minimal

		reactions: base
		in reaction to changes in minimal
		execute actions in minimal

		reaction ReactionA {
			after element «DEFAULT_TRIGGER»
			call routineA(newValue)
		}

		routine routineA(minimal::NonRoot nonRootElement) {
			update {
				«DEFAULT_STATEMENT»
			}
		}

		reactions: ext
		in reaction to changes in minimal
		execute actions in minimal
		import base

		«IF withOverrideRoutine»
			routine base::routineA(minimal::NonRoot nonRootElement) {
				update {
					nonRootElement.id = "overridden"
				}
			}
		«ENDIF»
	'''

	@Test
	def void testOverriddenRoutineChangesReactionHash() {
		val withoutOverride = generateAndExtractHashes(overrideRoutineSource(false), 'ext')
		val withOverride = generateAndExtractHashes(overrideRoutineSource(true), 'ext')
		assertThat(withoutOverride.keySet, is(#{'base::ReactionA'}))
		assertThat(withOverride.keySet, is(#{'base::ReactionA'}))
		// without an override, the imported reaction hashes identically to its original segment:
		val baseHashes = generateAndExtractHashes(overrideRoutineSource(false), 'base')
		assertThat(withoutOverride.get('base::ReactionA'), is(baseHashes.get('base::ReactionA')))
		// with the root segment overriding the called routine, the effectively executed routine differs:
		assertThat(withOverride.get('base::ReactionA'), is(not(withoutOverride.get('base::ReactionA'))))
	}

	@Test
	def void testSyntheticBuilderReactionsGetHashes() {
		val create = new FluentReactionsLanguageBuilder()
		val fileBuilder = create.reactionsFile('builderTest')
		fileBuilder += create.reactionsSegment('builderTest').inReactionToChangesIn(AllElementTypesPackage.eINSTANCE).
			executeActionsIn(AllElementTypesPackage.eINSTANCE) += create.reaction('TestReaction').afterAnyChange.call [
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

		val hashes = fsa.extractHashes('builderTest')
		assertThat(hashes.keySet, is(#{'builderTest::TestReaction'}))
		assertThat(HEX_HASH_PATTERN.matcher(hashes.get('builderTest::TestReaction')).matches, is(true))
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
