package tools.vitruv.dsls.reactions.migration.testspecs;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import pcm_mockup.Component;
import pcm_mockup.PInterface;
import pcm_mockup.PMethod;
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
        return mirrorLikeUmlToPcm(mirrorParent, sourceChild);
      }

      @Override
      protected void syncAttribute(EObject source, EObject mirror, EAttribute attribute) {
        copyAttribute(source, mirror, attribute, attribute.getName());
      }
    };
  }

  public static MirroringSpecification umlToPcmWithoutMethods() {
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

        return null;
      }

      @Override
      protected void syncAttribute(EObject source, EObject mirror, EAttribute attribute) {
        copyAttribute(source, mirror, attribute, attribute.getName());
      }
    };
  }

  public static MirroringSpecification umlToPcmWithoutInterfaces() {
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
        if (mirrorParent instanceof Repository repository && sourceChild instanceof UClass) {
          Component component = Pcm_mockupFactory.eINSTANCE.createComponent();
          repository.getComponents().add(component);
          return component;
        }

        return null;
      }

      @Override
      protected void syncAttribute(EObject source, EObject mirror, EAttribute attribute) {
        copyAttribute(source, mirror, attribute, attribute.getName());
      }
    };
  }

  public static MirroringSpecification umlToPcmWithInterfacesAsComponents() {
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
        if (mirrorParent instanceof Repository repository
            && (sourceChild instanceof UClass || sourceChild instanceof UInterface)) {
          Component component = Pcm_mockupFactory.eINSTANCE.createComponent();
          repository.getComponents().add(component);
          return component;
        }

        return null;
      }

      @Override
      protected void syncAttribute(EObject source, EObject mirror, EAttribute attribute) {
        copyAttribute(source, mirror, attribute, attribute.getName());
      }
    };
  }

  public static MirroringSpecification umlToPcmInRenamedFile() {
    return new MirroringSpecification(
        Uml_mockupPackage.eINSTANCE, Pcm_mockupPackage.eINSTANCE, PCM_EXTENSION) {
      @Override
      protected URI mirrorUriFor(String sourceUri) {
        URI stem = URI.createURI(sourceUri).trimFileExtension();
        return stem.trimSegments(1)
            .appendSegment(stem.lastSegment() + "-repository")
            .appendFileExtension(PCM_EXTENSION);
      }

      @Override
      protected EObject mirrorRoot(EObject sourceRoot) {
        return sourceRoot instanceof UPackage
            ? Pcm_mockupFactory.eINSTANCE.createRepository()
            : null;
      }

      @Override
      protected EObject mirrorChild(EObject mirrorParent, EObject sourceChild) {
        return mirrorLikeUmlToPcm(mirrorParent, sourceChild);
      }

      @Override
      protected void syncAttribute(EObject source, EObject mirror, EAttribute attribute) {
        copyAttribute(source, mirror, attribute, attribute.getName());
      }
    };
  }

  public static MirroringSpecification umlToPcmWithRenamedMethods() {
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
        return mirrorLikeUmlToPcm(mirrorParent, sourceChild);
      }

      @Override
      protected void syncAttribute(EObject source, EObject mirror, EAttribute attribute) {
        if (mirror instanceof PMethod method && "name".equals(attribute.getName())) {
          method.setName(source.eGet(attribute) + "Value");
          return;
        }

        copyAttribute(source, mirror, attribute, attribute.getName());
      }
    };
  }

  public static MirroringSpecification umlToPcmWithRenamedInterfaces() {
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
        return mirrorLikeUmlToPcm(mirrorParent, sourceChild);
      }

      @Override
      protected void syncAttribute(EObject source, EObject mirror, EAttribute attribute) {
        if (mirror instanceof PInterface pInterface && "name".equals(attribute.getName())) {
          pInterface.setName(source.eGet(attribute) + "Renamed");
          return;
        }

        copyAttribute(source, mirror, attribute, attribute.getName());
      }
    };
  }

  public static MirroringSpecification umlToPcmWithExtraMethods() {
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
        EObject mirror = mirrorLikeUmlToPcm(mirrorParent, sourceChild);
        if (mirror instanceof PMethod && mirrorParent instanceof PInterface pInterface) {
          pInterface.getMethods().add(extraMethod("first"));
          pInterface.getMethods().add(extraMethod("second"));
        }

        return mirror;
      }

      @Override
      protected void syncAttribute(EObject source, EObject mirror, EAttribute attribute) {
        copyAttribute(source, mirror, attribute, attribute.getName());
      }
    };
  }

  private static PMethod extraMethod(String name) {
    PMethod extra = Pcm_mockupFactory.eINSTANCE.createPMethod();
    extra.setName(name);
    return extra;
  }

  private static EObject mirrorLikeUmlToPcm(EObject mirrorParent, EObject sourceChild) {
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
      PMethod method = Pcm_mockupFactory.eINSTANCE.createPMethod();
      pInterface.getMethods().add(method);
      return method;
    }

    return null;
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
        if (mirrorParent instanceof UInterface uInterface && sourceChild instanceof PMethod) {
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
