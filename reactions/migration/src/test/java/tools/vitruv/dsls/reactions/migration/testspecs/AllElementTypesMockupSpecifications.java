package tools.vitruv.dsls.reactions.migration.testspecs;

import allElementTypes.AllElementTypesFactory;
import allElementTypes.AllElementTypesPackage;
import allElementTypes.NonRoot;
import allElementTypes.Root;
import allElementTypes2.AllElementTypes2Factory;
import allElementTypes2.AllElementTypes2Package;
import allElementTypes2.NonRoot2;
import allElementTypes2.Root2;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;

public final class AllElementTypesMockupSpecifications {
  public static final String ALL_ELEMENT_TYPES_2_EXTENSION = "allelementtypes2";

  private AllElementTypesMockupSpecifications() {}

  public static MirroringSpecification toAllElementTypes2() {
    return new MirroringSpecification(
        AllElementTypesPackage.eINSTANCE,
        AllElementTypes2Package.eINSTANCE,
        ALL_ELEMENT_TYPES_2_EXTENSION) {
      @Override
      protected EObject mirrorRoot(EObject sourceRoot) {
        return sourceRoot instanceof Root ? AllElementTypes2Factory.eINSTANCE.createRoot2() : null;
      }

      @Override
      protected EObject mirrorChild(EObject mirrorParent, EObject sourceChild) {
        if (mirrorParent instanceof Root2 root2 && sourceChild instanceof NonRoot) {
          NonRoot2 nonRoot2 = AllElementTypes2Factory.eINSTANCE.createNonRoot2();
          root2.getMultiValuedContainmentEReference2().add(nonRoot2);
          return nonRoot2;
        }

        return null;
      }

      @Override
      protected void syncAttribute(EObject source, EObject mirror, EAttribute attribute) {
        copyAttribute(source, mirror, attribute, attribute.getName() + "2");
      }
    };
  }

  public static MirroringSpecification toAllElementTypes() {
    return new MirroringSpecification(
        AllElementTypes2Package.eINSTANCE,
        AllElementTypesPackage.eINSTANCE,
        PcmAetMockupSpecifications.AET_EXTENSION) {
      @Override
      protected EObject mirrorRoot(EObject sourceRoot) {
        return sourceRoot instanceof Root2 ? AllElementTypesFactory.eINSTANCE.createRoot() : null;
      }

      @Override
      protected EObject mirrorChild(EObject mirrorParent, EObject sourceChild) {
        if (mirrorParent instanceof Root root && sourceChild instanceof NonRoot2) {
          NonRoot nonRoot = AllElementTypesFactory.eINSTANCE.createNonRoot();
          root.getMultiValuedContainmentEReference().add(nonRoot);
          return nonRoot;
        }

        return null;
      }

      @Override
      protected void syncAttribute(EObject source, EObject mirror, EAttribute attribute) {
        String name = attribute.getName();
        String targetName = name.endsWith("2") ? name.substring(0, name.length() - 1) : name;
        copyAttribute(source, mirror, attribute, targetName);
      }
    };
  }
}
