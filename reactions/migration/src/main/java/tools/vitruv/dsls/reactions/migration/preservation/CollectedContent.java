package tools.vitruv.dsls.reactions.migration.preservation;

import java.util.List;

public record CollectedContent(
    List<PreservationCandidate> candidates,
    List<OrphanResource> orphanResources,
    List<LostItem> losses,
    List<DecisionItem> decisions) {}
