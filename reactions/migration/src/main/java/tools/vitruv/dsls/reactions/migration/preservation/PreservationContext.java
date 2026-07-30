package tools.vitruv.dsls.reactions.migration.preservation;

import java.nio.file.Path;
import java.util.function.Predicate;
import org.eclipse.emf.ecore.EObject;
import tools.vitruv.change.interaction.InteractionResultProvider;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

public record PreservationContext(
    InternalVirtualModel vsum,
    Path vsumFolder,
    Path preMigrationFolder,
    AdapterRegistry adapters,
    Predicate<EObject> derivedRoot,
    PreservationPolicy policy,
    InteractionResultProvider interaction) {}
