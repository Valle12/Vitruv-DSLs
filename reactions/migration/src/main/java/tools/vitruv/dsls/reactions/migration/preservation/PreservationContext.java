package tools.vitruv.dsls.reactions.migration.preservation;

import java.nio.file.Path;
import java.util.function.Predicate;
import org.eclipse.emf.ecore.EObject;
import tools.vitruv.change.interaction.InteractionResultProvider;
import tools.vitruv.dsls.reactions.migration.adapter.AdapterRegistry;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

/**
 * What the preservation step needs to know about the migration it belongs to.
 *
 * @param vsum the migrated V-SUM
 * @param vsumFolder the folder that V-SUM lies in
 * @param preMigrationFolder a copy of the folder as it was before the migration
 * @param adapters the metamodel adapters
 * @param derivedRoot decides whether a root belongs to a model the migration re-derived
 * @param policy how far content is to be carried over
 * @param interaction what answers a question about an ambiguous placement
 */
public record PreservationContext(
    InternalVirtualModel vsum,
    Path vsumFolder,
    Path preMigrationFolder,
    AdapterRegistry adapters,
    Predicate<EObject> derivedRoot,
    PreservationPolicy policy,
    InteractionResultProvider interaction) {}
