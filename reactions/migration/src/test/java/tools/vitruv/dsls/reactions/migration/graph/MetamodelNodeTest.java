package tools.vitruv.dsls.reactions.migration.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.vitruv.change.composite.MetamodelDescriptor;

class MetamodelNodeTest {
  @Test
  @DisplayName("owns exactly the declared URI")
  void test1() {
    MetamodelNode node = MetamodelNode.of(Set.of("http://www.emftext.org/java"));

    assertTrue(node.owns("http://www.emftext.org/java"));
    assertFalse(node.owns("http://www.eclipse.org/uml2/5.0.0/UML"));
  }

  @Test
  @DisplayName("owns sub URIs of the declared URI")
  void test2() {
    MetamodelNode node = MetamodelNode.of(Set.of("http://www.emftext.org/java"));

    assertTrue(node.owns("http://www.emftext.org/java/classifiers"));
    assertFalse(node.owns("http://www.emftext.org/javax"));
  }

  @Test
  @DisplayName("short name is the first sorted URI")
  void test3() {
    MetamodelNode node = MetamodelNode.of(Set.of("http://b/2", "http://a/1"));

    assertEquals("http://a/1", node.shortName());
  }

  @Test
  @DisplayName("nodes from the same descriptor are equal")
  void test4() {
    MetamodelNode first = MetamodelNode.of(MetamodelDescriptor.with("http://families/1.0"));
    MetamodelNode second = MetamodelNode.of(Set.of("http://families/1.0"));

    assertEquals(first, second);
  }
}
