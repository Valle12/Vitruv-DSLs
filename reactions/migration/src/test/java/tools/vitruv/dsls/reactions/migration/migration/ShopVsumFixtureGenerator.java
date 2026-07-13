package tools.vitruv.dsls.reactions.migration.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.eclipse.emf.common.util.URI;
import org.emftext.language.java.JavaClasspath;
import org.emftext.language.java.classifiers.Class;
import org.emftext.language.java.classifiers.ConcreteClassifier;
import org.emftext.language.java.classifiers.Enumeration;
import org.emftext.language.java.classifiers.Interface;
import org.emftext.language.java.containers.CompilationUnit;
import org.emftext.language.java.containers.ContainersFactory;
import org.emftext.language.java.containers.Package;
import org.emftext.language.java.members.ClassMethod;
import org.emftext.language.java.members.Field;
import org.emftext.language.java.types.TypeReference;
import tools.vitruv.applications.umljava.JavaToUmlChangePropagationSpecification;
import tools.vitruv.applications.umljava.UmlToJavaChangePropagationSpecification;
import tools.vitruv.applications.util.temporary.java.JavaContainerAndClassifierUtil;
import tools.vitruv.applications.util.temporary.java.JavaMemberAndParameterUtil;
import tools.vitruv.applications.util.temporary.java.JavaModificationUtil;
import tools.vitruv.applications.util.temporary.java.JavaPersistenceHelper;
import tools.vitruv.applications.util.temporary.java.JavaSetup;
import tools.vitruv.applications.util.temporary.java.JavaStandardType;
import tools.vitruv.applications.util.temporary.java.JavaVisibility;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.testutils.integration.TestViewFactory;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

public final class ShopVsumFixtureGenerator {
  public static final String RESOURCE_PATH = "/persisted-shop-vsum";
  public static final String DEFAULT_TARGET =
      "reactions/migration/src/test/resources/persisted-shop-vsum";
  public static final String SENTINEL = "file:/__VSUM_ROOT__";

  private static final List<ChangePropagationSpecification> SPECIFICATIONS =
      List.of(
          new UmlToJavaChangePropagationSpecification(),
          new JavaToUmlChangePropagationSpecification());

  private ShopVsumFixtureGenerator() {}

  public static void main(String[] args) throws Exception {
    Path target = Path.of(args.length > 0 ? args[0] : DEFAULT_TARGET);

    Vsums.prepareStandalone(new AdapterRegistry());
    JavaSetup.resetClasspathAndRegisterStandardLibrary();

    Path work = Files.createTempDirectory("shop-fixture");
    InternalVirtualModel vsum =
        Vsums.build(
            work, SPECIFICATIONS, new TestUserInteraction.ResultProvider(permissiveInteraction()));
    try {
      seedInto(vsum, work);
    } finally {
      vsum.dispose();
    }

    deleteRecursively(target);
    copyTreeReplacing(
        work, target, URI.createFileURI(work.toAbsolutePath().toString()).toString(), SENTINEL);
    deleteRecursively(work);

    System.out.println("Wrote persisted VSUM fixture to " + target.toAbsolutePath());
  }

  @SuppressWarnings("UnstableApiUsage")
  private static void seedInto(InternalVirtualModel vsum, Path vsumFolder) throws Exception {
    List<ConcreteClassifier> classifiers = richShopClassifiers(true, false);
    TestViewFactory viewFactory = new TestViewFactory(vsum);
    View javaView =
        viewFactory.createViewOfElements(
            "Java packages and classes", Set.of(Package.class, CompilationUnit.class));
    viewFactory.changeViewRecordingChanges(
        javaView,
        committable -> {
          for (ConcreteClassifier classifier : classifiers) {
            CompilationUnit compilationUnit = ContainersFactory.eINSTANCE.createCompilationUnit();
            JavaContainerAndClassifierUtil.updateCompilationUnitName(
                compilationUnit, classifier.getName());
            compilationUnit.getClassifiers().add(classifier);
            URI uri =
                URI.createFileURI(
                    vsumFolder
                        .resolve(JavaPersistenceHelper.buildJavaFilePath(compilationUnit))
                        .toString());
            committable.registerRoot(compilationUnit, uri);
          }
        });
  }

  public static List<ConcreteClassifier> richShopClassifiers(
      boolean withRelationships, boolean withJdkStringField) {
    Interface identifiable =
        JavaContainerAndClassifierUtil.createJavaInterface("Identifiable", List.of());
    identifiable
        .getMembers()
        .add(JavaMemberAndParameterUtil.createJavaInterfaceMethod("getId", intType(), List.of()));

    Enumeration orderStatus =
        JavaContainerAndClassifierUtil.createJavaEnum(
            "OrderStatus",
            JavaVisibility.PUBLIC,
            JavaMemberAndParameterUtil.createJavaEnumConstantsFromList(
                List.of("OPEN", "PAID", "SHIPPED")));

    Class customer = publicJavaClass("Customer");
    if (withJdkStringField) {
      ConcreteClassifier stringClass =
          (ConcreteClassifier) JavaClasspath.get().getClassifier("java.lang.String");
      customer
          .getMembers()
          .add(
              privateField(
                  "name", JavaModificationUtil.createNamespaceClassifierReference(stringClass)));
    }
    customer.getMembers().add(privateField("age", intType()));
    customer.getMembers().add(publicIntMethod());

    Class premiumCustomer = publicJavaClass("PremiumCustomer");
    premiumCustomer.getMembers().add(privateField("discountPercent", intType()));

    Class order = publicJavaClass("Order");
    order.getMembers().add(privateField("total", intType()));
    order.getMembers().add(publicIntMethod());

    if (withRelationships) {
      customer
          .getImplements()
          .add(JavaModificationUtil.createNamespaceClassifierReference(identifiable));
      premiumCustomer.setExtends(JavaModificationUtil.createNamespaceClassifierReference(customer));
      order
          .getImplements()
          .add(JavaModificationUtil.createNamespaceClassifierReference(identifiable));
      order
          .getMembers()
          .add(
              privateField(
                  "status", JavaModificationUtil.createNamespaceClassifierReference(orderStatus)));
    }

    return List.of(identifiable, orderStatus, customer, premiumCustomer, order);
  }

  public static void copyTreeReplacing(Path source, Path target, String find, String replace)
      throws IOException {
    try (var walk = Files.walk(source)) {
      for (Path path : (Iterable<Path>) walk::iterator) {
        Path destination = target.resolve(source.relativize(path).toString());
        if (Files.isDirectory(path)) {
          Files.createDirectories(destination);
          continue;
        }
        Files.createDirectories(destination.getParent());
        Files.writeString(destination, Files.readString(path).replace(find, replace));
      }
    }
  }

  private static TestUserInteraction permissiveInteraction() {
    TestUserInteraction interaction = new TestUserInteraction();
    interaction.onTextInput(description -> true).always().respondWith("model");
    interaction
        .onMultipleChoiceSingleSelection(description -> true)
        .always()
        .respondWithChoiceAt(0);
    interaction.onConfirmation(description -> true).always().respondWith(true);
    interaction.acknowledgeNotification(description -> true);
    return interaction;
  }

  private static void deleteRecursively(Path folder) throws IOException {
    if (!Files.exists(folder)) {
      return;
    }
    try (var walk = Files.walk(folder)) {
      List<Path> paths = new ArrayList<>(walk.sorted(Comparator.reverseOrder()).toList());
      for (Path path : paths) {
        Files.deleteIfExists(path);
      }
    }
  }

  private static Class publicJavaClass(String name) {
    return JavaContainerAndClassifierUtil.createJavaClass(
        name, JavaVisibility.PUBLIC, false, false);
  }

  private static TypeReference intType() {
    return JavaStandardType.createJavaPrimitiveType(JavaStandardType.INT);
  }

  private static Field privateField(String name, TypeReference type) {
    return JavaMemberAndParameterUtil.createJavaAttribute(
        name, type, JavaVisibility.PRIVATE, false, false);
  }

  private static ClassMethod publicIntMethod() {
    return JavaMemberAndParameterUtil.createJavaClassMethod(
        "getId", intType(), JavaVisibility.PUBLIC, false, false, List.of());
  }
}
