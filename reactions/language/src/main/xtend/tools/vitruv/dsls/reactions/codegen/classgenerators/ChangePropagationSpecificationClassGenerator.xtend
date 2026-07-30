package tools.vitruv.dsls.reactions.codegen.classgenerators

import org.eclipse.emf.ecore.impl.EPackageImpl
import org.eclipse.xtext.common.types.JvmGenericType
import org.eclipse.xtext.common.types.JvmVisibility
import tools.vitruv.dsls.reactions.codegen.typesbuilder.TypesBuilderExtensionProvider
import tools.vitruv.dsls.reactions.language.toplevelelements.ReactionsSegment
import tools.vitruv.dsls.reactions.runtime.reactions.AbstractReactionsChangePropagationSpecification
import tools.vitruv.change.propagation.ChangePropagationSpecification

import static extension tools.vitruv.dsls.reactions.codegen.helper.ReactionsImportsHelper.getIncludedReactions
import static extension tools.vitruv.dsls.reactions.codegen.helper.ClassNamesGenerators.getReactionClassNameGenerator
import static extension tools.vitruv.dsls.reactions.codegen.helper.ClassNamesGenerators.getChangePropagationSpecificationClassNameGenerator
import static extension tools.vitruv.dsls.reactions.codegen.helper.ClassNamesGenerators.getRoutinesFacadesProviderClassNameGenerator
import static extension tools.vitruv.dsls.reactions.codegen.helper.ReactionsElementsCompletionChecker.isReferenceable
import static extension tools.vitruv.dsls.reactions.util.ReactionsLanguageUtil.getQualifiedName
import java.util.Map
import java.util.Set
import tools.vitruv.change.propagation.ConsistencyRuleId
import tools.vitruv.change.propagation.ConsistencyRuleTrigger
import tools.vitruv.change.propagation.ConsistencyRuleTrigger.ValueSlot
import tools.vitruv.dsls.reactions.codegen.helper.ConsistencyRuleHashCalculator
import tools.vitruv.dsls.reactions.codegen.helper.ConsistencyRuleTriggerCalculator

import static extension tools.vitruv.dsls.reactions.codegen.changetyperepresentation.ChangeTypeRepresentationExtractor.*
import tools.vitruv.change.composite.MetamodelDescriptor
import tools.vitruv.dsls.reactions.runtime.structure.ReactionsImportPath
import static com.google.common.base.Preconditions.checkState
import tools.vitruv.dsls.reactions.runtime.routines.RoutinesFacadesProvider
import tools.vitruv.dsls.reactions.runtime.state.ReactionExecutionState
import tools.vitruv.dsls.reactions.codegen.helper.AccessibleElement

class ChangePropagationSpecificationClassGenerator extends ClassGenerator {
	final ReactionsSegment reactionsSegment
	var JvmGenericType generatedClass

	new(ReactionsSegment reactionsSegment, TypesBuilderExtensionProvider typesBuilderExtensionProvider) {
		super(typesBuilderExtensionProvider)
		checkState(reactionsSegment.isReferenceable, "reactions segment is incomplete")
		this.reactionsSegment = reactionsSegment
	}

	override generateEmptyClass() {
		generatedClass = reactionsSegment.toClass(
			reactionsSegment.changePropagationSpecificationClassNameGenerator.qualifiedName) [
			visibility = JvmVisibility.PUBLIC
		]
	}

	override generateBody() {
		generatedClass => [
			superTypes += typeRef(AbstractReactionsChangePropagationSpecification)
			superTypes += typeRef(ChangePropagationSpecification)
			members += reactionsSegment.toConstructor() [
				body = '''
				super(«MetamodelDescriptor».with(«Set».of(«FOR namespaceUri : reactionsSegment.fromMetamodels.map[package.nsURI] SEPARATOR ','»"«namespaceUri»"«ENDFOR»)), 
					«MetamodelDescriptor».with(«Set».of(«FOR namespaceUri : reactionsSegment.toMetamodels.map[package.nsURI] SEPARATOR ','»"«namespaceUri»"«ENDFOR»)));'''
			]
			
			// create routines facades provider:
			members += reactionsSegment.toMethod("createRoutinesFacadesProvider", typeRef(RoutinesFacadesProvider)) [
				visibility = JvmVisibility.PROTECTED
				val executionStateParameter = generateParameter(new AccessibleElement("executionState", ReactionExecutionState))
				parameters += executionStateParameter
				body = '''
					return new «reactionsSegment.routinesFacadesProviderClassNameGenerator.qualifiedName»(«executionStateParameter.name»);
				'''
			]

			// register all reactions, including imported and overridden reactions:
			members += reactionsSegment.toMethod("setup", typeRef(Void.TYPE)) [
				visibility = JvmVisibility.PROTECTED
				val metamodelPackageClassQualifiedNames = (reactionsSegment.fromMetamodels +
					reactionsSegment.toMetamodels).map[package.class].filter[it !== EPackageImpl].map[name]
				body = '''
					«FOR metamodelPackageClassQualifiedName : metamodelPackageClassQualifiedNames»org.eclipse.emf.ecore.EPackage.Registry.INSTANCE.putIfAbsent(«
						metamodelPackageClassQualifiedName».eNS_URI, «metamodelPackageClassQualifiedName».eINSTANCE);
					«ENDFOR»
					«FOR reactionEntry : reactionsSegment.includedReactions.entrySet.filter[key.isReferenceable]»
						«val reaction = reactionEntry.key»
						«val reactionsImportPath = reactionEntry.value»
						«val reactionsNameGenerator = reaction.reactionClassNameGenerator»
						addReaction(new «reactionsNameGenerator.qualifiedName»((executionState) -> createRoutinesFacadesProvider(executionState).getRoutinesFacade(«
							»«ReactionsImportPath».fromPathString("«reactionsImportPath.pathString»"))));
					«ENDFOR»
				'''
			]

			// expose the semantic hash of each registered reaction (rule), computed at code generation time:
			members += reactionsSegment.toMethod("getConsistencyRuleHashes",
				typeRef(Map, typeRef(ConsistencyRuleId), typeRef(String))) [
				visibility = JvmVisibility.PUBLIC
				body = '''
					«val hashCalculator = new ConsistencyRuleHashCalculator(reactionsSegment, jvmModelAssociations)»
					return «Map».ofEntries(«
					»«FOR reaction : reactionsSegment.includedReactions.keySet.filter[isReferenceable] SEPARATOR ','»
						«Map».entry(new «ConsistencyRuleId»("«reaction.qualifiedName»"), "«hashCalculator.computeRuleHash(reaction)»")«
					»«ENDFOR»);
				'''
			]

			// expose the trigger descriptor of each registered reaction (rule), computed at code generation time:
			members += reactionsSegment.toMethod("getConsistencyRuleTriggers",
				typeRef(Map, typeRef(ConsistencyRuleId), typeRef(ConsistencyRuleTrigger))) [
				visibility = JvmVisibility.PUBLIC
				body = '''
					return «Map».ofEntries(«
					»«FOR reaction : reactionsSegment.includedReactions.keySet.filter[isReferenceable] SEPARATOR ','»
						«val trigger = ConsistencyRuleTriggerCalculator.computeTrigger(reaction.trigger.extractChangeType)»
						«Map».entry(new «ConsistencyRuleId»("«reaction.qualifiedName»"), new «ConsistencyRuleTrigger»(«
						»"«trigger.changeType»", "«trigger.affectedType»", "«trigger.affectedFeature»", "«trigger.valueType»", «
						»«ValueSlot».«trigger.valueSlot.name»))«
					»«ENDFOR»);
				'''
			]
		]
	}
}
