package tools.vitruv.dsls.reactions.tests

import com.google.inject.Inject
import com.google.inject.Provider
import org.eclipse.xtext.resource.XtextResourceSet
import org.eclipse.xtext.testing.InjectWith
import org.eclipse.xtext.testing.extensions.InjectionExtension
import org.eclipse.xtext.testing.util.ParseHelper
import org.eclipse.xtext.testing.validation.ValidationTestHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.^extension.ExtendWith
import tools.vitruv.dsls.reactions.language.toplevelelements.ReactionsFile
import tools.vitruv.dsls.reactions.language.toplevelelements.TopLevelElementsPackage

import static java.nio.file.Files.createTempDirectory
import static org.eclipse.xtext.diagnostics.Diagnostic.LINKING_DIAGNOSTIC
import static org.hamcrest.CoreMatchers.is
import static org.hamcrest.MatcherAssert.assertThat

@ExtendWith(InjectionExtension)
@InjectWith(ReactionsLanguageInjectorProvider)
class AnnotationCompilationTest {
	@Inject TestReactionsCompiler.Factory compilerFactory
	@Inject ParseHelper<ReactionsFile> parseHelper
	@Inject ValidationTestHelper validationHelper
	@Inject Provider<XtextResourceSet> resourceSetProvider

	@Test
	def void testAnnotatedFileCompiles() {
		val compilationDir = createTempDirectory("reactions-annotation-compilation")
		val compiler = compilerFactory.createCompiler [
			reactionsOwner = this
			compilationProjectDir = compilationDir
			reactions = #['/tools/vitruv/dsls/reactions/tests/AnnotationBasic.reactions']
			changePropagationSegments = #['annotationBasic']
		]

		val specifications = compiler.changePropagationSpecifications.toList
		assertThat(specifications.size, is(1))
		assertThat(specifications.head !== null, is(true))
	}

	@Test
	def void testFeatureAnnotationProducesValidationError() {
		val source = '''
			import "http://tools.vitruv.change.testutils.metamodels.allElementTypes" as minimal

			reactions: annotationStillPresent
			in reaction to changes in minimal
			execute actions in minimal

			@feature(type="basic")
			reaction InsertedNonRootAnnotated {
				after element minimal::NonRoot inserted in minimal::NonRootObjectContainerHelper[nonRootObjectsContainment]
				call insertNonRoot(newValue)
			}

			routine insertNonRoot(minimal::NonRoot nonRootElement) {
				match {
					require absence of minimal::NonRoot corresponding to nonRootElement
				}
				create {
					val newNonRoot = new minimal::NonRoot
				}
				update {
					newNonRoot.id = nonRootElement.id
				}
			}
		'''

		val parsed = parseHelper.parse(source, resourceSetProvider.get())
		validationHelper.assertError(
			parsed,
			TopLevelElementsPackage.Literals.REACTIONS_FILE,
			null,
			"Found '@feature' annotation in reactions source",
			"Run the reactions preprocessor"
		)
	}

	@Test
	def void testNoFeatureAnnotationErrorOnPreprocessedSource() {
		val source = '''
			import "http://tools.vitruv.change.testutils.metamodels.allElementTypes" as minimal

			reactions: annotationStripped
			in reaction to changes in minimal
			execute actions in minimal

			reaction InsertedNonRootAnnotated {
				after element minimal::NonRoot inserted in minimal::NonRootObjectContainerHelper[nonRootObjectsContainment]
				call insertNonRoot(newValue)
			}

			routine insertNonRoot(minimal::NonRoot nonRootElement) {
				match {
					require absence of minimal::NonRoot corresponding to nonRootElement
				}
				create {
					val newNonRoot = new minimal::NonRoot
				}
				update {
					newNonRoot.id = nonRootElement.id
				}
			}
		'''

		val parsed = parseHelper.parse(source, resourceSetProvider.get())
		validationHelper.validate(parsed).forEach [ issue |
			if (issue.code != LINKING_DIAGNOSTIC) {
				assertThat(
					"Unexpected validation issue: " + issue.message,
					issue.message.contains("@feature"),
					is(false)
				)
			}
		]
	}
}
