package tools.vitruv.dsls.reactions.migration.testspecs;

import allElementTypes.AllElementTypesFactory;
import allElementTypes.AllElementTypesPackage;
import allElementTypes.NonRoot;
import allElementTypes.Root;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import pcm_mockup.Component;
import pcm_mockup.Pcm_mockupFactory;
import pcm_mockup.Pcm_mockupPackage;
import pcm_mockup.Repository;

public final class PcmAetMockupSpecifications {
  public static final String AET_EXTENSION = "allelementtypes";

  private PcmAetMockupSpecifications() {}

  public static MirroringSpecification pcmToAet() {
    return new MirroringSpecification(
        Pcm_mockupPackage.eINSTANCE, AllElementTypesPackage.eINSTANCE, AET_EXTENSION) {
      @Override
      protected EObject mirrorRoot(EObject sourceRoot) {
        return sourceRoot instanceof Repository
            ? AllElementTypesFactory.eINSTANCE.createRoot()
            : null;
      }

      @Override
      protected EObject mirrorChild(EObject mirrorParent, EObject sourceChild) {
        if (mirrorParent instanceof Root root && sourceChild instanceof Component) {
          NonRoot nonRoot = AllElementTypesFactory.eINSTANCE.createNonRoot();
          root.getMultiValuedContainmentEReference().add(nonRoot);
          return nonRoot;
        }

        return null;
      }

      @Override
      protected void syncAttribute(EObject source, EObject mirror, EAttribute attribute) {
        if ("name".equals(attribute.getName())) {
          copyAttribute(source, mirror, attribute, "value");
        } else {
          copyAttribute(source, mirror, attribute, attribute.getName());
        }
      }
    };
  }

  public static MirroringSpecification aetToPcm() {
    return new MirroringSpecification(
        AllElementTypesPackage.eINSTANCE,
        Pcm_mockupPackage.eINSTANCE,
        UmlPcmMockupSpecifications.PCM_EXTENSION) {
      @Override
      protected EObject mirrorRoot(EObject sourceRoot) {
        return sourceRoot instanceof Root ? Pcm_mockupFactory.eINSTANCE.createRepository() : null;
      }

      @Override
      protected EObject mirrorChild(EObject mirrorParent, EObject sourceChild) {
        if (mirrorParent instanceof Repository repository && sourceChild instanceof NonRoot) {
          Component component = Pcm_mockupFactory.eINSTANCE.createComponent();
          repository.getComponents().add(component);
          return component;
        }

        return null;
      }

      @Override
      protected void syncAttribute(EObject source, EObject mirror, EAttribute attribute) {
        if ("value".equals(attribute.getName())) {
          copyAttribute(source, mirror, attribute, "name");
        } else {
          copyAttribute(source, mirror, attribute, attribute.getName());
        }
      }
    };
  }
}
