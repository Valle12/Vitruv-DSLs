package tools.vitruv.dsls.reactions.migration.migration;

import static tools.vitruv.dsls.reactions.migration.migration.LibraryCliFixture.permissiveInteraction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.applications.umljava.JavaToUmlChangePropagationSpecification;
import tools.vitruv.applications.umljava.UmlToJavaChangePropagationSpecification;
import tools.vitruv.applications.util.temporary.java.JavaSetup;
import tools.vitruv.change.testutils.RegisterMetamodelsInStandalone;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.dsls.reactions.migration.spec.SpecificationSource;
import tools.vitruv.dsls.reactions.migration.vsum.Vsums;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

@Slf4j
@ExtendWith(RegisterMetamodelsInStandalone.class)
class UmlJavaLibraryBaselineTest {
  private static final AdapterRegistry ADAPTERS = new AdapterRegistry();
  private static final SpecificationSource SPECS =
      () ->
          List.of(
              new UmlToJavaChangePropagationSpecification(),
              new JavaToUmlChangePropagationSpecification());

  private static final Map<String, String> EXPECTED_CLASSIFIERS = expectedClassifiers();

  @TempDir Path tempDir;

  private Path vsumFolder;
  private InternalVirtualModel vsum;

  @BeforeAll
  static void prepareStandalone() {
    Vsums.prepareStandalone(ADAPTERS);
  }

  private static Map<String, String> expectedClassifiers() {
    Map<String, String> expected = new LinkedHashMap<>();
    expected.put(LibraryExampleModel.IDENTIFIABLE, "interface public  | method public getId():int");
    expected.put(
        LibraryExampleModel.BORROWABLE,
        "interface public  | extends Identifiable | method public borrow(memberId:int):boolean");
    expected.put(LibraryExampleModel.MEDIA_TYPE, "enum public  | constants=BOOK,BLU_RAY,MAGAZINE");
    expected.put(
        LibraryExampleModel.ISBN,
        "class public  | field public code:String | method public getCode():String"
            + " | method public setCode(code:String):void");
    expected.put(
        LibraryExampleModel.MEDIA,
        "class public abstract  | implements Borrowable"
            + " | field public title:String | method public getTitle():String"
            + " | method public setTitle(title:String):void"
            + " | field protected mediaId:int | method public getMediaId():int"
            + " | method public setMediaId(mediaId:int):void"
            + " | field public type:MediaType | method public getType():MediaType"
            + " | method public setType(type:MediaType):void"
            + " | field public weight:double | method public getWeight():double"
            + " | method public setWeight(weight:double):void"
            + " | method public describe():void");
    expected.put(
        LibraryExampleModel.BOOK,
        "class public abstract  | extends Media"
            + " | field private isbn:Isbn | method public getIsbn():Isbn"
            + " | method public setIsbn(isbn:Isbn):void"
            + " | field public pageCount:int | method public getPageCount():int"
            + " | method public setPageCount(pageCount:int):void"
            + " | method public final describe():void");
    expected.put(
        LibraryExampleModel.LIBRARY_CARD,
        "class public final  | field public static cardNumber:String"
            + " | method public getCardNumber():String"
            + " | method public setCardNumber(cardNumber:String):void");
    expected.put(
        LibraryExampleModel.MEMBER,
        "class public  | field public name:String | method public getName():String"
            + " | method public setName(name:String):void"
            + " | field public active:boolean | method public getActive():boolean"
            + " | method public setActive(active:boolean):void"
            + " | field public borrowed:ArrayList<Media> | method public getBorrowed():ArrayList<Media>"
            + " | method public setBorrowed(borrowed:ArrayList<Media>):void"
            + " | method public static register(card:LibraryCard):void"
            + " | method public totalWeight():double");
    return expected;
  }

  @BeforeEach
  void buildVsum() throws IOException {
    JavaSetup.resetClasspathAndRegisterStandardLibrary();
    vsumFolder = Files.createDirectories(tempDir.resolve("library-vsum"));
    vsum =
        Vsums.build(
            vsumFolder,
            SPECS.createSpecifications(),
            new TestUserInteraction.ResultProvider(permissiveInteraction()));
  }

  @AfterEach
  void disposeVsum() {
    if (vsum != null) {
      vsum.dispose();
      vsum = null;
    }
  }

  @Test
  @DisplayName("the library model derives a consistent Java model")
  void test1() throws Exception {
    seedLibraryModel();
    vsum.dispose();
    vsum = null;

    logProducedJavaSources();
    Map<String, String> actualClassifiers = ModelStructure.ofJava(vsumFolder, ADAPTERS);
    logOutline(actualClassifiers);
    ModelStructure.reportDeviations(
        "the derived Java model", EXPECTED_CLASSIFIERS, actualClassifiers);
  }

  private void seedLibraryModel() throws Exception {
    LibraryExampleModel.seedInto(vsum, vsumFolder);
  }

  private void logProducedJavaSources() throws IOException {
    log.info("=== produced Java sources under {} ===", vsumFolder);
    for (Path file : ModelStructure.modelFiles(vsumFolder, ".java")) {
      log.info("--- {} ---\n{}", vsumFolder.relativize(file), Files.readString(file));
    }
  }

  private void logOutline(Map<String, String> actualClassifiers) {
    log.info("=== produced classifier outline ===");
    actualClassifiers.forEach((name, description) -> log.info("{} -> {}", name, description));
  }
}
