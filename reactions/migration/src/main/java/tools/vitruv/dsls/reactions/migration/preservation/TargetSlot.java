package tools.vitruv.dsls.reactions.migration.preservation;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

public record TargetSlot(EObject owner, EStructuralFeature feature) {}
