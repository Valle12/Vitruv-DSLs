package tools.vitruv.dsls.reactions.tests

import com.google.inject.Inject
import org.eclipse.xtext.testing.InjectWith
import org.eclipse.xtext.testing.extensions.InjectionExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.^extension.ExtendWith

import static java.nio.file.Files.createTempDirectory
import static org.hamcrest.CoreMatchers.is
import static org.hamcrest.MatcherAssert.assertThat

@ExtendWith(InjectionExtension)
@InjectWith(ReactionsLanguageInjectorProvider)
class AnnotationCompilationTest {
	@Inject TestReactionsCompiler.Factory compilerFactory

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
}