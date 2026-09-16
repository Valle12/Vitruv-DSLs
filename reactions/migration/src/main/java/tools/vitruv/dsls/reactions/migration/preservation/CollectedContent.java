package tools.vitruv.dsls.reactions.migration.preservation;

import java.util.List;

/**
 * What the preservation step found in the old state, before anything is put back.
 *
 * @param candidates the values that could be carried over, each with the place it would go
 * @param orphanResources the model files the migration left without a counterpart
 * @param losses the values that cannot be kept, each with the reason
 * @param decisions what was settled along the way and what nobody could settle
 */
public record CollectedContent(
    List<PreservationCandidate> candidates,
    List<OrphanResource> orphanResources,
    List<LostItem> losses,
    List<DecisionItem> decisions) {}
