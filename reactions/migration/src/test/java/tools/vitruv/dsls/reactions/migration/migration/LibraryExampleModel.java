package tools.vitruv.dsls.reactions.migration.migration;

import java.nio.file.Path;
import java.util.Set;
import org.eclipse.emf.common.util.URI;
import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.DataType;
import org.eclipse.uml2.uml.Enumeration;
import org.eclipse.uml2.uml.Interface;
import org.eclipse.uml2.uml.LiteralUnlimitedNatural;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.Operation;
import org.eclipse.uml2.uml.Parameter;
import org.eclipse.uml2.uml.ParameterDirectionKind;
import org.eclipse.uml2.uml.PrimitiveType;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.Type;
import org.eclipse.uml2.uml.UMLFactory;
import org.eclipse.uml2.uml.VisibilityKind;
import tools.vitruv.framework.testutils.integration.TestViewFactory;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

public final class LibraryExampleModel {
  public static final String MODEL_NAME = "library";
  public static final String PACKAGE_NAME = "catalog";

  public static final String IDENTIFIABLE = "Identifiable";
  public static final String BORROWABLE = "Borrowable";
  public static final String MEDIA_TYPE = "MediaType";
  public static final String ISBN = "Isbn";
  public static final String MEDIA = "Media";
  public static final String BOOK = "Book";
  public static final String LIBRARY_CARD = "LibraryCard";
  public static final String MEMBER = "Member";

  private LibraryExampleModel() {}

  public static void seedInto(InternalVirtualModel vsum, Path vsumFolder) throws Exception {
    seedInto(vsum, vsumFolder, build(), MODEL_NAME + ".uml");
  }

  @SuppressWarnings("UnstableApiUsage")
  public static void seedInto(
      InternalVirtualModel vsum, Path vsumFolder, Model root, String fileName) throws Exception {
    TestViewFactory viewFactory = new TestViewFactory(vsum);
    View umlView = viewFactory.createViewOfElements("UML", Set.of(Model.class));
    viewFactory.changeViewRecordingChanges(
        umlView,
        committable ->
            committable.registerRoot(
                root,
                URI.createFileURI(
                    vsumFolder.resolve("model").resolve(fileName).toAbsolutePath().toString())));
  }

  public static Model build() {
    Model model = UMLFactory.eINSTANCE.createModel();
    model.setName(MODEL_NAME);

    PrimitiveType intType = createPrimitiveType(model, "int");
    PrimitiveType booleanType = createPrimitiveType(model, "boolean");
    PrimitiveType stringType = createPrimitiveType(model, "String");
    PrimitiveType doubleType = createPrimitiveType(model, "double");

    org.eclipse.uml2.uml.Package catalog = model.createNestedPackage(PACKAGE_NAME);

    Interface identifiable = catalog.createOwnedInterface(IDENTIFIABLE);
    createOperation(identifiable, "getId", intType);

    Interface borrowable = catalog.createOwnedInterface(BORROWABLE);
    borrowable.createGeneralization(identifiable);
    Operation borrow = createOperation(borrowable, "borrow", booleanType);
    createInParameter(borrow, "memberId", intType);

    Enumeration mediaType = catalog.createOwnedEnumeration(MEDIA_TYPE);
    mediaType.createOwnedLiteral("BOOK");
    mediaType.createOwnedLiteral("BLU_RAY");
    mediaType.createOwnedLiteral("MAGAZINE");

    DataType isbn = UMLFactory.eINSTANCE.createDataType();
    isbn.setName(ISBN);
    catalog.getPackagedElements().add(isbn);
    isbn.createOwnedAttribute("code", stringType);

    Class media = catalog.createOwnedClass(MEDIA, true);
    media.createInterfaceRealization(BORROWABLE, borrowable);
    media.createOwnedAttribute("title", stringType);
    Property mediaId = media.createOwnedAttribute("mediaId", intType);
    mediaId.setVisibility(VisibilityKind.PROTECTED_LITERAL);
    media.createOwnedAttribute("type", mediaType);
    media.createOwnedAttribute("weight", doubleType);
    createOperation(media, "describe", null);

    Class book = catalog.createOwnedClass(BOOK, true);
    book.createGeneralization(media);
    Property bookIsbn = book.createOwnedAttribute("isbn", isbn);
    bookIsbn.setVisibility(VisibilityKind.PRIVATE_LITERAL);
    book.createOwnedAttribute("pageCount", intType);
    Operation bookDescribe = createOperation(book, "describe", null);
    bookDescribe.setIsLeaf(true);

    Class libraryCard = catalog.createOwnedClass(LIBRARY_CARD, false);
    libraryCard.setIsFinalSpecialization(true);
    Property cardNumber = libraryCard.createOwnedAttribute("cardNumber", stringType);
    cardNumber.setIsStatic(true);

    Class member = catalog.createOwnedClass(MEMBER, false);
    member.createOwnedAttribute("name", stringType);
    member.createOwnedAttribute("active", booleanType);
    Property borrowed = member.createOwnedAttribute("borrowed", media);
    borrowed.setUpper(LiteralUnlimitedNatural.UNLIMITED);
    Operation register = createOperation(member, "register", null);
    createInParameter(register, "card", libraryCard);
    register.setIsStatic(true);
    createOperation(member, "totalWeight", doubleType);

    return model;
  }

  private static PrimitiveType createPrimitiveType(Model model, String name) {
    PrimitiveType primitiveType = UMLFactory.eINSTANCE.createPrimitiveType();
    primitiveType.setName(name);
    model.getPackagedElements().add(primitiveType);
    return primitiveType;
  }

  private static Operation createOperation(Class owner, String name, Type returnType) {
    return owner.createOwnedOperation(name, null, null, returnType);
  }

  private static Operation createOperation(Interface owner, String name, Type returnType) {
    return owner.createOwnedOperation(name, null, null, returnType);
  }

  private static void createInParameter(Operation operation, String name, Type type) {
    Parameter parameter = operation.createOwnedParameter(name, type);
    parameter.setDirection(ParameterDirectionKind.IN_LITERAL);
  }
}
