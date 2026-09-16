package tools.vitruv.dsls.reactions.migration.preservation;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * The place a value is to be put, namely one feature of one migrated element.
 *
 * @param owner the migrated element the value goes on
 * @param feature the feature of that element it goes into
 */
public record TargetSlot(EObject owner, EStructuralFeature feature) {}
