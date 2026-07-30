package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.emftext.language.java.classifiers.Class;
import org.emftext.language.java.classifiers.ConcreteClassifier;
import org.emftext.language.java.containers.CompilationUnit;
import org.emftext.language.java.containers.ContainersFactory;
import org.emftext.language.java.generics.QualifiedTypeArgument;
import org.emftext.language.java.literals.DecimalDoubleLiteral;
import org.emftext.language.java.literals.LiteralsFactory;
import org.emftext.language.java.members.ClassMethod;
import org.emftext.language.java.members.Field;
import org.emftext.language.java.members.Member;
import org.emftext.language.java.operators.OperatorsFactory;
import org.emftext.language.java.parameters.OrdinaryParameter;
import org.emftext.language.java.parameters.ParametersFactory;
import org.emftext.language.java.references.IdentifierReference;
import org.emftext.language.java.references.MethodCall;
import org.emftext.language.java.references.ReferencesFactory;
import org.emftext.language.java.statements.Block;
import org.emftext.language.java.statements.ForEachLoop;
import org.emftext.language.java.statements.LocalVariableStatement;
import org.emftext.language.java.statements.StatementsFactory;
import org.emftext.language.java.types.TypeReference;
import org.emftext.language.java.types.TypesFactory;
import org.emftext.language.java.variables.LocalVariable;
import org.emftext.language.java.variables.VariablesFactory;
import tools.vitruv.applications.util.temporary.java.JavaContainerAndClassifierUtil;
import tools.vitruv.applications.util.temporary.java.JavaMemberAndParameterUtil;
import tools.vitruv.applications.util.temporary.java.JavaModificationUtil;
import tools.vitruv.applications.util.temporary.java.JavaPersistenceHelper;
import tools.vitruv.applications.util.temporary.java.JavaStandardType;
import tools.vitruv.applications.util.temporary.java.JavaStatementUtil;
import tools.vitruv.applications.util.temporary.java.JavaVisibility;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

public final class LibraryUserContent {
  public static final String BODIED_METHOD = "describe";

  public static final String COMPUTED_METHOD = "totalWeight";

  public static final String HAND_WRITTEN_METHOD = "renew";

  public static final String HAND_WRITTEN_UNIT = "LibraryUtils";

  public static final int COMPUTED_METHOD_STATEMENTS = 3;

  private static final String BORROWED_FIELD = "borrowed";
  private static final String WEIGHT_GETTER = "getWeight";
  private static final String TOTAL_VARIABLE = "total";
  private static final String ELEMENT_VARIABLE = "media";

  private LibraryUserContent() {}

  public static void writeMethodBody(
      InternalVirtualModel vsum, String classifierName, String methodName) throws Exception {
    try (View view = Vsums.openViewOfAll(vsum, "write-body")) {
      CommittableView committable = view.withChangeRecordingTrait();
      methodIn(committable.getRootObjects(), classifierName, methodName)
          .getStatements()
          .add(StatementsFactory.eINSTANCE.createReturn());
      committable.commitChanges();
    }
  }

  public static boolean derivesTotalWeightBody(InternalVirtualModel vsum) throws Exception {
    try (View view = Vsums.openViewOfAll(vsum, "total-weight-body-probe")) {
      Iterable<EObject> roots = view.getRootObjects();
      return holdsClassMethod(roots) && holdsBorrowedField(roots) && holdsWeightGetter(roots);
    }
  }

  private static boolean holdsClassMethod(Iterable<? extends EObject> roots) {
    try {
      methodIn(roots, LibraryExampleModel.MEMBER, LibraryUserContent.COMPUTED_METHOD);
      return true;
    } catch (AssertionError missing) {
      return false;
    }
  }

  private static boolean holdsBorrowedField(Iterable<? extends EObject> roots) {
    try {
      fieldIn(roots);
      return true;
    } catch (AssertionError missing) {
      return false;
    }
  }

  private static boolean holdsWeightGetter(Iterable<? extends EObject> roots) {
    try {
      methodIn(borrowedElementType(roots), WEIGHT_GETTER);
      return true;
    } catch (AssertionError missing) {
      return false;
    }
  }

  public static void writeTotalWeightBody(InternalVirtualModel vsum) throws Exception {
    try (View view = Vsums.openViewOfAll(vsum, "total-weight-body")) {
      CommittableView committable = view.withChangeRecordingTrait();
      Iterable<EObject> roots = committable.getRootObjects();
      ClassMethod totalWeight = methodIn(roots, LibraryExampleModel.MEMBER, COMPUTED_METHOD);
      ConcreteClassifier element = borrowedElementType(roots);
      LocalVariableStatement declaration = declareTotal();
      LocalVariable total = declaration.getVariable();

      totalWeight.getStatements().add(declaration);
      totalWeight
          .getStatements()
          .add(sumInto(total, fieldIn(roots), element, methodIn(element, WEIGHT_GETTER)));
      totalWeight
          .getStatements()
          .add(
              JavaStatementUtil.createReturnStatement(
                  JavaStatementUtil.createIdentifierReference(total)));
      committable.commitChanges();
    }
  }

  private static LocalVariableStatement declareTotal() {
    DecimalDoubleLiteral zero = LiteralsFactory.eINSTANCE.createDecimalDoubleLiteral();
    zero.setDecimalValue(0d);
    LocalVariable total = VariablesFactory.eINSTANCE.createLocalVariable();
    total.setName(TOTAL_VARIABLE);
    total.setTypeReference(TypesFactory.eINSTANCE.createDouble());
    total.setInitialValue(zero);

    LocalVariableStatement declaration = StatementsFactory.eINSTANCE.createLocalVariableStatement();
    declaration.setVariable(total);
    return declaration;
  }

  private static ForEachLoop sumInto(
      LocalVariable total, Field borrowed, ConcreteClassifier element, ClassMethod weightGetter) {
    OrdinaryParameter borrowedElement = ParametersFactory.eINSTANCE.createOrdinaryParameter();
    borrowedElement.setName(ELEMENT_VARIABLE);
    borrowedElement.setTypeReference(
        JavaModificationUtil.createNamespaceClassifierReference(element));

    ForEachLoop loop = StatementsFactory.eINSTANCE.createForEachLoop();
    loop.setNext(borrowedElement);
    loop.setCollection(JavaStatementUtil.createSelfReferenceToAttribute(borrowed));
    loop.setStatement(addWeightOf(borrowedElement, weightGetter, total));
    return loop;
  }

  private static Block addWeightOf(
      OrdinaryParameter borrowedElement, ClassMethod weightGetter, LocalVariable total) {
    MethodCall weightOfElement = ReferencesFactory.eINSTANCE.createMethodCall();
    weightOfElement.setTarget(weightGetter);
    IdentifierReference elementReference =
        JavaStatementUtil.createIdentifierReference(borrowedElement);
    elementReference.setNext(weightOfElement);

    Block body = StatementsFactory.eINSTANCE.createBlock();
    body.getStatements()
        .add(
            JavaStatementUtil.wrapExpressionInExpressionStatement(
                JavaStatementUtil.createAssignmentExpression(
                    JavaStatementUtil.createIdentifierReference(total),
                    OperatorsFactory.eINSTANCE.createAssignmentPlus(),
                    elementReference)));
    return body;
  }

  public static void addHandWrittenMethod(
      InternalVirtualModel vsum, String classifierName, String methodName) throws Exception {
    try (View view = Vsums.openViewOfAll(vsum, "hand-written-method")) {
      CommittableView committable = view.withChangeRecordingTrait();
      classifierIn(committable.getRootObjects(), classifierName)
          .getMembers()
          .add(
              JavaMemberAndParameterUtil.createJavaClassMethod(
                  methodName,
                  JavaStandardType.createJavaPrimitiveType(JavaStandardType.INT),
                  JavaVisibility.PUBLIC,
                  false,
                  false,
                  List.of()));
      committable.commitChanges();
    }
  }

  @SuppressWarnings("UnstableApiUsage")
  public static void addHandWrittenCompilationUnit(
      InternalVirtualModel vsum, Path vsumFolder, String classifierName) throws Exception {
    try (View view = Vsums.openViewOfAll(vsum, "hand-written-unit")) {
      CommittableView committable = view.withChangeRecordingTrait();
      Class handWritten =
          JavaContainerAndClassifierUtil.createJavaClass(
              classifierName, JavaVisibility.PUBLIC, false, false);
      CompilationUnit unit = ContainersFactory.eINSTANCE.createCompilationUnit();
      JavaContainerAndClassifierUtil.updateCompilationUnitName(unit, classifierName);
      unit.getClassifiers().add(handWritten);
      committable.registerRoot(
          unit,
          URI.createFileURI(
              vsumFolder.resolve(JavaPersistenceHelper.buildJavaFilePath(unit)).toString()));
      committable.commitChanges();
    }
  }

  public static int statementCount(
      Path vsumFolder, AdapterRegistry adapters, String classifierName, String methodName)
      throws IOException {
    List<EObject> roots = loadClassifierFile(vsumFolder, adapters, classifierName);
    return methodIn(roots, classifierName, methodName).getStatements().size();
  }

  public static Set<String> methodNames(
      Path vsumFolder, AdapterRegistry adapters, String classifierName) throws IOException {
    return classifierIn(loadClassifierFile(vsumFolder, adapters, classifierName), classifierName)
        .getMembers()
        .stream()
        .filter(ClassMethod.class::isInstance)
        .map(Member::getName)
        .collect(Collectors.toUnmodifiableSet());
  }

  public static boolean hasJavaFile(Path vsumFolder, String classifierName) throws IOException {
    return findClassifierFile(vsumFolder, classifierName).isPresent();
  }

  private static List<EObject> loadClassifierFile(
      Path vsumFolder, AdapterRegistry adapters, String classifierName) throws IOException {
    Path file =
        findClassifierFile(vsumFolder, classifierName)
            .orElseThrow(
                () -> new AssertionError("no " + classifierName + ".java below " + vsumFolder));
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet.getLoadOptions().putAll(adapters.combinedLoadOptions());
    Resource resource = resourceSet.getResource(URI.createFileURI(file.toString()), true);
    assertTrue(resource.getErrors().isEmpty(), "the Java model must load: " + file);
    return resource.getContents();
  }

  private static Optional<Path> findClassifierFile(Path vsumFolder, String classifierName)
      throws IOException {
    String fileName = classifierName + ".java";
    return ModelStructure.modelFiles(vsumFolder, ".java").stream()
        .filter(path -> path.getFileName().toString().equals(fileName))
        .findFirst();
  }

  private static ConcreteClassifier classifierIn(
      Iterable<? extends EObject> roots, String classifierName) {
    for (EObject root : roots) {
      if (root instanceof CompilationUnit unit) {
        for (ConcreteClassifier classifier : unit.getClassifiers()) {
          if (classifierName.equals(classifier.getName())) {
            return classifier;
          }
        }
      }
    }

    throw new AssertionError("the reactions did not derive a Java classifier " + classifierName);
  }

  private static ClassMethod methodIn(
      Iterable<? extends EObject> roots, String classifierName, String methodName) {
    return methodIn(classifierIn(roots, classifierName), methodName);
  }

  private static ClassMethod methodIn(ConcreteClassifier classifier, String methodName) {
    for (Member member : classifier.getMembers()) {
      if (member instanceof ClassMethod method && methodName.equals(method.getName())) {
        return method;
      }
    }

    throw new AssertionError(
        "the reactions did not derive a Java method " + classifier.getName() + "." + methodName);
  }

  private static ConcreteClassifier borrowedElementType(Iterable<? extends EObject> roots) {
    TypeReference fieldType = fieldIn(roots).getTypeReference();
    Iterator<EObject> nested = fieldType.eAllContents();
    while (nested.hasNext()) {
      if (nested.next() instanceof QualifiedTypeArgument argument
          && argument.getTypeReference().getTarget() instanceof ConcreteClassifier element) {
        return element;
      }
    }

    if (fieldType.getTarget() instanceof ConcreteClassifier element) {
      return element;
    }

    throw new AssertionError(
        "the reactions did not derive an element type for "
            + LibraryExampleModel.MEMBER
            + "."
            + BORROWED_FIELD);
  }

  private static Field fieldIn(Iterable<? extends EObject> roots) {
    for (Member member : classifierIn(roots, LibraryExampleModel.MEMBER).getMembers()) {
      if (member instanceof Field field
          && LibraryUserContent.BORROWED_FIELD.equals(field.getName())) {
        return field;
      }
    }

    throw new AssertionError(
        "the reactions did not derive a Java field "
            + LibraryExampleModel.MEMBER
            + "."
            + LibraryUserContent.BORROWED_FIELD);
  }
}
