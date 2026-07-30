package tools.vitruv.dsls.reactions.migration.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.uml2.uml.*;
import org.emftext.language.java.classifiers.ConcreteClassifier;
import org.emftext.language.java.classifiers.Enumeration;
import org.emftext.language.java.containers.CompilationUnit;
import org.emftext.language.java.generics.QualifiedTypeArgument;
import org.emftext.language.java.members.Field;
import org.emftext.language.java.members.Member;
import org.emftext.language.java.members.Method;
import org.emftext.language.java.modifiers.Abstract;
import org.emftext.language.java.modifiers.AnnotableAndModifiable;
import org.emftext.language.java.modifiers.Final;
import org.emftext.language.java.modifiers.Modifier;
import org.emftext.language.java.modifiers.Private;
import org.emftext.language.java.modifiers.Protected;
import org.emftext.language.java.modifiers.Public;
import org.emftext.language.java.modifiers.Static;
import org.emftext.language.java.types.ClassifierReference;
import org.emftext.language.java.types.NamespaceClassifierReference;
import org.emftext.language.java.types.TypeReference;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;

final class ModelStructure {
  private static final Set<String> JDK_NAMESPACE_FOLDERS = Set.of("java", "javax", "sun", "jdk");

  private ModelStructure() {}

  static Map<String, String> ofJava(Path folder, AdapterRegistry adapters) throws IOException {
    ResourceSet resourceSet = resourceSetFor(adapters);
    Map<String, String> classifiers = new LinkedHashMap<>();
    for (Path file : modelFiles(folder, ".java")) {
      resourceOf(resourceSet, file).getContents().stream()
          .filter(CompilationUnit.class::isInstance)
          .map(CompilationUnit.class::cast)
          .flatMap(unit -> unit.getClassifiers().stream())
          .forEach(classifier -> classifiers.put(classifier.getName(), describeJava(classifier)));
    }

    return classifiers;
  }

  static Map<String, String> ofUml(Path folder, AdapterRegistry adapters) throws IOException {
    ResourceSet resourceSet = resourceSetFor(adapters);
    Map<String, String> classifiers = new LinkedHashMap<>();
    for (Path file : modelFiles(folder, ".uml")) {
      resourceOf(resourceSet, file).getContents().stream()
          .filter(Model.class::isInstance)
          .map(Model.class::cast)
          .forEach(model -> collectUmlClassifiers(model, classifiers));
    }

    return classifiers;
  }

  static Comparison compare(Map<String, String> expected, Map<String, String> actual) {
    List<String> content = new ArrayList<>();
    List<String> ordering = new ArrayList<>();
    for (String deviation : deviations(expected, actual)) {
      (isOrderingOnly(deviation) ? ordering : content).add(deviation);
    }

    return new Comparison(content, ordering);
  }

  private static boolean isOrderingOnly(String deviation) {
    if (!deviation.startsWith("DIFFERS")) {
      return false;
    }

    List<String> expected = partsAfter(deviation, "\n  expected: ");
    return !expected.isEmpty() && expected.equals(partsAfter(deviation, "\n  actual:   "));
  }

  private static List<String> partsAfter(String deviation, String label) {
    int start = deviation.indexOf(label);
    if (start < 0) {
      return List.of();
    }

    int end = deviation.indexOf('\n', start + label.length());
    String description =
        end < 0
            ? deviation.substring(start + label.length())
            : deviation.substring(start + label.length(), end);
    return Stream.of(description.split(" \\| ")).sorted().toList();
  }

  static List<String> deviations(Map<String, String> expected, Map<String, String> actual) {
    List<String> deviations = new ArrayList<>();
    expected.forEach(
        (name, description) -> {
          String actualDescription = actual.get(name);
          if (actualDescription == null) {
            deviations.add("MISSING  " + name + "\n  expected: " + description);
          } else if (!actualDescription.equals(description)) {
            deviations.add(
                "DIFFERS  "
                    + name
                    + "\n  expected: "
                    + description
                    + "\n  actual:   "
                    + actualDescription);
          }
        });
    actual.keySet().stream()
        .filter(name -> !expected.containsKey(name))
        .forEach(name -> deviations.add("UNEXPECTED  " + name + " -> " + actual.get(name)));

    return deviations;
  }

  static void reportDeviations(
      String label, Map<String, String> expected, Map<String, String> actual) {
    List<String> deviations = deviations(expected, actual);
    if (!deviations.isEmpty()) {
      fail(
          label
              + " deviates in "
              + deviations.size()
              + " place(s):\n"
              + String.join("\n", deviations));
    }
  }

  static List<Path> modelFiles(Path folder, String extension) throws IOException {
    try (var walk = Files.walk(folder)) {
      return walk.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(extension))
          .filter(path -> isOutsideJdkStandardLibrary(folder, path))
          .sorted()
          .toList();
    }
  }

  static boolean isOutsideJdkStandardLibrary(Path folder, Path file) {
    for (Path segment : folder.relativize(file)) {
      if (JDK_NAMESPACE_FOLDERS.contains(segment.toString())) {
        return false;
      }
    }

    return true;
  }

  private static ResourceSet resourceSetFor(AdapterRegistry adapters) {
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet.getLoadOptions().putAll(adapters.combinedLoadOptions());
    return resourceSet;
  }

  private static Resource resourceOf(ResourceSet resourceSet, Path file) {
    Resource resource = resourceSet.getResource(URI.createFileURI(file.toString()), true);
    assertTrue(
        resource.getErrors().isEmpty(),
        () -> "model must load without errors: " + file + "\nerrors: " + resource.getErrors());
    return resource;
  }

  private static void collectUmlClassifiers(
      org.eclipse.uml2.uml.Package umlPackage, Map<String, String> classifiers) {
    for (PackageableElement element : umlPackage.getPackagedElements()) {
      if (element instanceof org.eclipse.uml2.uml.Package nested) {
        collectUmlClassifiers(nested, classifiers);
      } else if (element instanceof Classifier classifier) {
        classifiers.put(classifier.getName(), describeUml(classifier));
      }
    }
  }

  private static String describeUml(Classifier classifier) {
    List<String> parts = new ArrayList<>();
    parts.add(umlKindOf(classifier) + umlClassifierModifiers(classifier));
    classifier.getGeneralizations().stream()
        .filter(generalization -> generalization.getGeneral() != null)
        .forEach(
            generalization -> parts.add("generalizes " + generalization.getGeneral().getName()));
    if (classifier instanceof org.eclipse.uml2.uml.Class umlClass) {
      umlClass.getInterfaceRealizations().stream()
          .filter(realization -> realization.getContract() != null)
          .forEach(realization -> parts.add("realizes " + realization.getContract().getName()));
    }
    if (classifier instanceof org.eclipse.uml2.uml.Enumeration enumeration) {
      parts.add(
          "literals="
              + enumeration.getOwnedLiterals().stream()
                  .map(NamedElement::getName)
                  .collect(Collectors.joining(",")));
    }

    umlAttributesOf(classifier).forEach(property -> parts.add(describeUmlProperty(property)));
    umlOperationsOf(classifier).forEach(operation -> parts.add(describeUmlOperation(operation)));
    return String.join(" | ", parts);
  }

  private static String umlKindOf(Classifier classifier) {
    if (classifier instanceof PrimitiveType) {
      return "primitiveType";
    }
    if (classifier instanceof org.eclipse.uml2.uml.Enumeration) {
      return "enumeration";
    }
    if (classifier instanceof Interface) {
      return "interface";
    }
    if (classifier instanceof DataType) {
      return "dataType";
    }

    return "class";
  }

  private static String umlClassifierModifiers(Classifier classifier) {
    String modifiers = "";
    if (classifier.isAbstract()) {
      modifiers += " abstract";
    }
    if (classifier.isFinalSpecialization()) {
      modifiers += " final";
    }

    return modifiers;
  }

  private static List<Property> umlAttributesOf(Classifier classifier) {
    if (classifier instanceof org.eclipse.uml2.uml.Class umlClass) {
      return umlClass.getOwnedAttributes();
    }
    if (classifier instanceof Interface umlInterface) {
      return umlInterface.getOwnedAttributes();
    }
    if (classifier instanceof DataType dataType) {
      return dataType.getOwnedAttributes();
    }

    return List.of();
  }

  private static List<Operation> umlOperationsOf(Classifier classifier) {
    if (classifier instanceof org.eclipse.uml2.uml.Class umlClass) {
      return umlClass.getOwnedOperations();
    }
    if (classifier instanceof Interface umlInterface) {
      return umlInterface.getOwnedOperations();
    }
    if (classifier instanceof DataType dataType) {
      return dataType.getOwnedOperations();
    }

    return List.of();
  }

  private static String describeUmlProperty(Property property) {
    String modifiers = property.getVisibility().getName();
    if (property.isStatic()) {
      modifiers += " static";
    }
    if (property.isReadOnly()) {
      modifiers += " readOnly";
    }
    String multiplicity =
        property.getUpper() == LiteralUnlimitedNatural.UNLIMITED || property.getUpper() > 1
            ? "["
                + property.getLower()
                + ".."
                + (property.getUpper() < 0 ? "*" : property.getUpper())
                + "]"
            : "";
    return "attribute "
        + modifiers
        + " "
        + property.getName()
        + ":"
        + umlTypeName(property.getType())
        + multiplicity;
  }

  private static String describeUmlOperation(Operation operation) {
    String modifiers = operation.getVisibility().getName();
    if (operation.isStatic()) {
      modifiers += " static";
    }
    if (operation.isAbstract()) {
      modifiers += " abstract";
    }
    String parameters =
        operation.getOwnedParameters().stream()
            .filter(parameter -> parameter.getDirection() == ParameterDirectionKind.IN_LITERAL)
            .map(parameter -> parameter.getName() + ":" + umlTypeName(parameter.getType()))
            .collect(Collectors.joining(","));
    return "operation "
        + modifiers
        + " "
        + operation.getName()
        + "("
        + parameters
        + "):"
        + umlTypeName(returnTypeOf(operation));
  }

  private static org.eclipse.uml2.uml.Type returnTypeOf(Operation operation) {
    return operation.getOwnedParameters().stream()
        .filter(parameter -> parameter.getDirection() == ParameterDirectionKind.RETURN_LITERAL)
        .findFirst()
        .map(Parameter::getType)
        .orElse(null);
  }

  private static String umlTypeName(org.eclipse.uml2.uml.Type type) {
    return type == null ? "void" : type.getName();
  }

  private static String describeJava(ConcreteClassifier classifier) {
    List<String> parts = new ArrayList<>();
    parts.add(javaKindOf(classifier) + " " + javaModifiersOf(classifier));
    javaSupertypesOf(classifier).ifPresent(parts::add);
    if (classifier instanceof Enumeration enumeration) {
      parts.add(
          "constants="
              + enumeration.getConstants().stream()
                  .map(org.emftext.language.java.commons.NamedElement::getName)
                  .collect(Collectors.joining(",")));
    }

    classifier.getMembers().stream().map(ModelStructure::describeJavaMember).forEach(parts::add);
    return String.join(" | ", parts);
  }

  private static String javaKindOf(ConcreteClassifier classifier) {
    if (classifier instanceof org.emftext.language.java.classifiers.Interface) {
      return "interface";
    }
    if (classifier instanceof Enumeration) {
      return "enum";
    }

    return "class";
  }

  private static Optional<String> javaSupertypesOf(ConcreteClassifier classifier) {
    List<String> supertypes = new ArrayList<>();
    if (classifier instanceof org.emftext.language.java.classifiers.Class javaClass) {
      if (javaClass.getExtends() != null) {
        supertypes.add("extends " + javaTypeName(javaClass.getExtends()));
      }
      javaClass
          .getImplements()
          .forEach(reference -> supertypes.add("implements " + javaTypeName(reference)));
    }
    if (classifier instanceof org.emftext.language.java.classifiers.Interface javaInterface) {
      javaInterface
          .getExtends()
          .forEach(reference -> supertypes.add("extends " + javaTypeName(reference)));
    }

    return supertypes.isEmpty() ? Optional.empty() : Optional.of(String.join(",", supertypes));
  }

  private static String describeJavaMember(Member member) {
    if (member instanceof Field field) {
      return "field "
          + javaModifiersOf(field)
          + field.getName()
          + ":"
          + javaTypeName(field.getTypeReference());
    }
    if (member instanceof Method method) {
      String parameters =
          method.getParameters().stream()
              .map(
                  parameter ->
                      parameter.getName() + ":" + javaTypeName(parameter.getTypeReference()))
              .collect(Collectors.joining(","));
      return "method "
          + javaModifiersOf(method)
          + method.getName()
          + "("
          + parameters
          + "):"
          + javaTypeName(method.getTypeReference());
    }

    return "member " + member.getName();
  }

  private static String javaModifiersOf(Object element) {
    if (!(element instanceof AnnotableAndModifiable modifiable)) {
      return "";
    }

    StringBuilder modifiers = new StringBuilder();
    for (Modifier modifier : modifiable.getModifiers()) {
      if (modifier instanceof Public) {
        modifiers.append("public ");
      } else if (modifier instanceof Protected) {
        modifiers.append("protected ");
      } else if (modifier instanceof Private) {
        modifiers.append("private ");
      } else if (modifier instanceof Static) {
        modifiers.append("static ");
      } else if (modifier instanceof Final) {
        modifiers.append("final ");
      } else if (modifier instanceof Abstract) {
        modifiers.append("abstract ");
      }
    }

    return modifiers.toString();
  }

  private static String javaTypeName(TypeReference reference) {
    switch (reference) {
      case null -> {
        return "void";
      }
      case NamespaceClassifierReference namespaced -> {
        List<ClassifierReference> nested = namespaced.getClassifierReferences();
        return nested.isEmpty() ? "?" : javaTypeName(nested.getLast());
      }
      case ClassifierReference classifierReference -> {
        org.emftext.language.java.classifiers.Classifier target = classifierReference.getTarget();
        String name = target == null ? "?" : target.getName();
        return name + javaTypeArgumentsOf(classifierReference);
      }
      default -> {
        // Should not happen
      }
    }

    return reference.eClass().getName().toLowerCase(Locale.ROOT);
  }

  private static String javaTypeArgumentsOf(ClassifierReference reference) {
    if (reference.getTypeArguments().isEmpty()) {
      return "";
    }

    return reference.getTypeArguments().stream()
        .map(
            argument ->
                argument instanceof QualifiedTypeArgument qualified
                    ? javaTypeName(qualified.getTypeReference())
                    : argument.eClass().getName())
        .collect(Collectors.joining(",", "<", ">"));
  }

  record Comparison(List<String> content, List<String> ordering) {
    boolean agrees() {
      return content.isEmpty() && ordering.isEmpty();
    }
  }
}
