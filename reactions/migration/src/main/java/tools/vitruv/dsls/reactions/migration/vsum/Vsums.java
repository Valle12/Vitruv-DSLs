package tools.vitruv.dsls.reactions.migration.vsum;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
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
import tools.vitruv.framework.vsum.helper.VsumFileSystemLayout;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

/**
 * Builds V-SUMs and opens views on them, including the registration a run outside an Eclipse
 * workbench has to perform for itself.
 */
@Slf4j
public final class Vsums {
  private static final String VSUM_METADATA_FOLDER = "vsum";

  private Vsums() {}

  /**
   * Builds a V-SUM over the given folder that propagates with the given rule set.
   *
   * @param folder the storage folder of the V-SUM
   * @param specifications the rule set to propagate with
   * @param interaction what answers an interaction a reaction asks for
   * @return the initialized V-SUM
   * @throws UncheckedIOException if the V-SUM cannot be built
   */
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

  /**
   * Drops the entries of the V-SUM's model registry that name no file, such as a platform
   * library an earlier run recorded, and leaves a V-SUM without a registry untouched.
   *
   * @param folder the folder of the persisted V-SUM
   * @throws UncheckedIOException if the registry cannot be rewritten
   */
  public static void normalizeModelRegistry(Path folder) {
    if (!Files.isDirectory(folder.resolve(VSUM_METADATA_FOLDER))) {
      return;
    }

    try {
      VsumFileSystemLayout layout = new VsumFileSystemLayout(folder);
      layout.prepare();
      Path modelsFile = layout.getModelsNamesFilesPath();
      if (!Files.exists(modelsFile)) {
        return;
      }

      List<String> entries = Files.readAllLines(modelsFile);
      List<String> kept = entries.stream().filter(entry -> URI.createURI(entry).isFile()).toList();
      if (kept.size() < entries.size()) {
        Files.write(modelsFile, kept);
        log.debug(
            "Dropped {} platform-library entry(ies) from the model registry of {}",
            entries.size() - kept.size(),
            folder);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to normalize the model registry of " + folder.toAbsolutePath(), e);
    }
  }

  /**
   * Registers what EMF and the metamodels need when no Eclipse workbench provides it.
   *
   * @param adapters the metamodel adapters to prepare along with EMF
   */
  public static void prepareStandalone(AdapterRegistry adapters) {
    EcorePlugin.ExtensionProcessor.process(null);
    adapters.prepareStandalone();
    registerFallbackXmiFactory();
  }

  /**
   * Opens a view holding every element the V-SUM offers.
   *
   * @param provider the V-SUM to open the view on
   * @param name the name the view type is created under
   * @return the opened view
   */
  public static View openViewOfAll(ViewProvider provider, String name) {
    return openViewOf(provider, name, element -> true);
  }

  /**
   * Opens a view holding the elements the given filter accepts.
   *
   * @param provider the V-SUM to open the view on
   * @param name the name the view type is created under
   * @param rootFilter decides for each selectable element whether the view holds it
   * @return the opened view
   */
  public static View openViewOf(ViewProvider provider, String name, Predicate<EObject> rootFilter) {
    ViewSelector selector =
        provider.createSelector(ViewTypeFactory.createIdentityMappingViewType(name));
    selector
        .getSelectableElements()
        .forEach(element -> selector.setSelected(element, rootFilter.test(element)));
    return selector.createView();
  }

  /**
   * Opens a view holding nothing, which elements may then be committed into.
   *
   * @param provider the V-SUM to open the view on
   * @param name the name the view type is created under
   * @return the opened view
   */
  public static View openEmptyView(ViewProvider provider, String name) {
    return provider
        .createSelector(ViewTypeFactory.createIdentityMappingViewType(name))
        .createView();
  }

  /**
   * Returns the roots the V-SUM holds itself, read without opening a view.
   *
   * @param vsum the V-SUM to read
   * @return the roots of its source models
   */
  public static List<EObject> liveRoots(InternalVirtualModel vsum) {
    List<EObject> roots = new ArrayList<>();
    for (Resource resource : List.copyOf(vsum.getViewSourceModels())) {
      roots.addAll(resource.getContents());
    }

    return roots;
  }

  /**
   * Returns the roots of the V-SUM, read through a view that is closed again straight away.
   *
   * @param provider the V-SUM to read
   * @return its root objects
   * @throws IllegalStateException if the view cannot be opened or read
   */
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
