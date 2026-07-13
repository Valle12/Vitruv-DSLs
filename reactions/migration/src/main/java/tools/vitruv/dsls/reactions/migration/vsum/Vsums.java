package tools.vitruv.dsls.reactions.migration.vsum;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.plugin.EcorePlugin;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import tools.vitruv.change.interaction.InteractionResultProvider;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.ViewProvider;
import tools.vitruv.framework.views.ViewSelector;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

public final class Vsums {
  private Vsums() {}

  public static InternalVirtualModel build(
      Path folder,
      List<ChangePropagationSpecification> specifications,
      InteractionResultProvider interaction) {
    try {
      return new VirtualModelBuilder()
          .withStorageFolder(folder)
          .withUserInteractorForResultProvider(interaction)
          .withChangePropagationSpecifications(specifications)
          .buildAndInitialize();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to build VSUM at " + folder.toAbsolutePath(), e);
    }
  }

  public static void prepareStandalone(AdapterRegistry adapters) {
    EcorePlugin.ExtensionProcessor.process(null);
    adapters.prepareStandalone();
    registerFallbackXmiFactory();
  }

  public static View openViewOfAll(ViewProvider provider, String name) {
    return openViewOf(provider, name, element -> true);
  }

  public static View openViewOf(
      ViewProvider provider, String name, Predicate<EObject> rootFilter) {
    ViewSelector selector =
        provider.createSelector(ViewTypeFactory.createIdentityMappingViewType(name));
    selector
        .getSelectableElements()
        .forEach(element -> selector.setSelected(element, rootFilter.test(element)));
    return selector.createView();
  }

  public static View openEmptyView(ViewProvider provider, String name) {
    return provider
        .createSelector(ViewTypeFactory.createIdentityMappingViewType(name))
        .createView();
  }

  public static List<EObject> readRoots(ViewProvider provider) {
    try (View view = openViewOfAll(provider, "read-all")) {
      return new ArrayList<>(view.getRootObjects());
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to read the VSUM's root objects", e);
    }
  }

  private static void registerFallbackXmiFactory() {
    Resource.Factory.Registry.INSTANCE
        .getExtensionToFactoryMap()
        .putIfAbsent(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
  }
}
