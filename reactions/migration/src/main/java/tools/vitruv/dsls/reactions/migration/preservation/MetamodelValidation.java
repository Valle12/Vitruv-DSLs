package tools.vitruv.dsls.reactions.migration.preservation;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.Diagnostician;

final class MetamodelValidation {
  private final Map<EObject, Integer> baselines = new IdentityHashMap<>();

  private static int errorsOfElement(EObject element) {
    BasicDiagnostic diagnostics = new BasicDiagnostic();
    Diagnostician.INSTANCE.validate(element.eClass(), element, diagnostics, new HashMap<>());
    return countErrors(diagnostics);
  }

  private static int errorsOfSubtree(EObject element) {
    return countErrors(Diagnostician.INSTANCE.validate(element));
  }

  private static int countErrors(Diagnostic diagnostic) {
    if (diagnostic.getChildren().isEmpty()) {
      return diagnostic.getSeverity() >= Diagnostic.ERROR ? 1 : 0;
    }

    return (int)
        diagnostic.getChildren().stream()
            .filter(child -> child.getSeverity() >= Diagnostic.ERROR)
            .count();
  }

  void remember(EObject owner, Object value) {
    baselines.put(owner, errorsOfElement(owner));
    if (value instanceof EObject element) {
      baselines.put(element, errorsOfSubtree(element));
    }
  }

  boolean regressed(EObject owner, Object originalValue, Object insertedValue) {
    if (errorsOfElement(owner) > baselines.getOrDefault(owner, 0)) {
      return true;
    }

    return originalValue instanceof EObject original
        && insertedValue instanceof EObject inserted
        && errorsOfSubtree(inserted) > baselines.getOrDefault(original, 0);
  }
}
