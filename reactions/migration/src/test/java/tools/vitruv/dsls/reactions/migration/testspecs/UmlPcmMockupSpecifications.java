package tools.vitruv.dsls.reactions.migration.testspecs;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import pcm_mockup.Component;
import pcm_mockup.PInterface;
import pcm_mockup.Pcm_mockupFactory;
import pcm_mockup.Pcm_mockupPackage;
import pcm_mockup.Repository;
import uml_mockup.UClass;
import uml_mockup.UInterface;
import uml_mockup.UMethod;
import uml_mockup.UPackage;
import uml_mockup.Uml_mockupFactory;
import uml_mockup.Uml_mockupPackage;

public final class UmlPcmMockupSpecifications {
  public static final String UML_EXTENSION = "uml_mockup";
  public static final String PCM_EXTENSION = "pcm_mockup";

  private UmlPcmMockupSpecifications() {}

  public static MirroringSpecification umlToPcm() {
    return new MirroringSpecification(
        Uml_mockupPackage.eINSTANCE, Pcm_mockupPackage.eINSTANCE, PCM_EXTENSION) {
      @Override
      protected EObject mirrorRoot(EObject sourceRoot) {
        return sourceRoot instanceof UPackage
            ? Pcm_mockupFactory.eINSTANCE.createRepository()
            : null;
      }

      @Override
      protected EObject mirrorChild(EObject mirrorParent, EObject sourceChild) {
        if (mirrorParent instanceof Repository repository) {
          if (sourceChild instanceof UClass) {
            Component component = Pcm_mockupFactory.eINSTANCE.createComponent();
            repository.getComponents().add(component);
            return component;
          }
          if (sourceChild instanceof UInterface) {
            PInterface pInterface = Pcm_mockupFactory.eINSTANCE.createPInterface();
            repository.getInterfaces().add(pInterface);
            return pInterface;
          }
        }
        if (mirrorParent instanceof PInterface pInterface && sourceChild instanceof UMethod) {
          var method = Pcm_mockupFactory.eINSTANCE.createPMethod();
          pInterface.getMethods().add(method);
          return method;
        }

        return null;
      }

      @Override
      protected void syncAttribute(EObject source, EObject mirror, EAttribute attribute) {
        copyAttribute(source, mirror, attribute, attribute.getName());
      }
    };
  }

  public static MirroringSpecification pcmToUml() {
    return new MirroringSpecification(
        Pcm_mockupPackage.eINSTANCE, Uml_mockupPackage.eINSTANCE, UML_EXTENSION) {
      @Override
      protected EObject mirrorRoot(EObject sourceRoot) {
        return sourceRoot instanceof Repository
            ? Uml_mockupFactory.eINSTANCE.createUPackage()
            : null;
      }

      @Override
      protected EObject mirrorChild(EObject mirrorParent, EObject sourceChild) {
        if (mirrorParent instanceof UPackage uPackage) {
          if (sourceChild instanceof Component) {
            UClass uClass = Uml_mockupFactory.eINSTANCE.createUClass();
            uPackage.getClasses().add(uClass);
            return uClass;
          }
          if (sourceChild instanceof PInterface) {
            UInterface uInterface = Uml_mockupFactory.eINSTANCE.createUInterface();
            uPackage.getInterfaces().add(uInterface);
            return uInterface;
          }
        }
        if (mirrorParent instanceof UInterface uInterface
            && sourceChild instanceof pcm_mockup.PMethod) {
          UMethod method = Uml_mockupFactory.eINSTANCE.createUMethod();
          uInterface.getMethods().add(method);
          return method;
        }

        return null;
      }

      @Override
      protected void syncAttribute(EObject source, EObject mirror, EAttribute attribute) {
        copyAttribute(source, mirror, attribute, attribute.getName());
      }
    };
  }
}
