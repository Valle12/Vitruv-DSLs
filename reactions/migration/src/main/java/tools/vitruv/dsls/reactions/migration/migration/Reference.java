package tools.vitruv.dsls.reactions.migration.migration;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;

public record Reference(EObject holder, EReference feature, EObject target) {}