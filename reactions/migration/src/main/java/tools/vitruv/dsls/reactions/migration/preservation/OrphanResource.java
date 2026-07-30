package tools.vitruv.dsls.reactions.migration.preservation;

import java.util.List;
import org.eclipse.emf.ecore.EObject;

public record OrphanResource(String resourceKey, List<EObject> roots) {}
