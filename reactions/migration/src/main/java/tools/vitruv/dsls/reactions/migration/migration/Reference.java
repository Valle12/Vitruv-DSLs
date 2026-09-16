package tools.vitruv.dsls.reactions.migration.migration;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;

/**
 * One reference out of a model element, remembered while it is detached from its holder.
 *
 * @param holder the element the reference leads out of
 * @param feature the reference feature it sat in
 * @param target the element it pointed at
 */
public record Reference(EObject holder, EReference feature, EObject target) {}