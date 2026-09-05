package tools.vitruv.dsls.reactions.migration.migration;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;

public record SourceSelection(List<MetamodelNode> present, List<Trial> trials, long changeCount) {
  public static final long NOT_MEASURED = -1;

  public SourceSelection {
    present = List.copyOf(present);
    trials = List.copyOf(trials);
  }

  public static SourceSelection none() {
    return new SourceSelection(List.of(), List.of(), NOT_MEASURED);
  }

  static SourceSelection of(List<MetamodelNode> present, List<Trial> trials) {
    return new SourceSelection(present, trials, NOT_MEASURED);
  }

  public SourceSelection withChangeCount(long count) {
    return new SourceSelection(present, trials, count);
  }

  public boolean changeCountMeasured() {
    return changeCount >= 0;
  }

  public Optional<Trial> trialOf(MetamodelNode candidate) {
    return trials.stream().filter(trial -> trial.candidate().equals(candidate)).findFirst();
  }

  public record Trial(
      MetamodelNode candidate, long changeCount, Duration duration, Optional<String> failure) {
    public static Trial completed(MetamodelNode candidate, long changeCount, Duration duration) {
      return new Trial(candidate, changeCount, duration, Optional.empty());
    }

    public static Trial failed(MetamodelNode candidate, Duration duration, Throwable reason) {
      return new Trial(candidate, NOT_MEASURED, duration, Optional.of(describe(reason)));
    }

    private static String describe(Throwable reason) {
      Throwable root = reason;
      while (root.getCause() != null && root.getCause() != root) {
        root = root.getCause();
      }

      String message = root.getMessage() == null ? root.toString() : root.getMessage();
      return root == reason ? message : root.getClass().getSimpleName() + ": " + message;
    }

    public boolean completed() {
      return failure.isEmpty();
    }
  }
}
