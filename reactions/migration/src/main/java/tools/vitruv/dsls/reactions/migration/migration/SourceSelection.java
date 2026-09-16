package tools.vitruv.dsls.reactions.migration.migration;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import tools.vitruv.dsls.reactions.migration.graph.MetamodelNode;

/**
 * How the dominant model was chosen, and what the strategy had to try to get there.
 *
 * @param present the metamodels the V-SUM held a model for
 * @param trials one entry per candidate the fewest changes strategy tried, empty under the
 *     other strategies
 * @param changeCount the changes the chosen candidate proposed, or {@link #NOT_MEASURED}
 */
public record SourceSelection(List<MetamodelNode> present, List<Trial> trials, long changeCount) {
  /** The change count of a selection whose strategy measured none. */
  public static final long NOT_MEASURED = -1;

  /** Copies the given lists, so that the selection cannot change under whoever reads it. */
  public SourceSelection {
    present = List.copyOf(present);
    trials = List.copyOf(trials);
  }

  /**
   * Returns the selection of a run that chose nothing.
   *
   * @return an empty selection with no measured change count
   */
  public static SourceSelection none() {
    return new SourceSelection(List.of(), List.of(), NOT_MEASURED);
  }

  static SourceSelection of(List<MetamodelNode> present, List<Trial> trials) {
    return new SourceSelection(present, trials, NOT_MEASURED);
  }

  /**
   * Returns the same selection with the changes of the chosen candidate filled in.
   *
   * @param count how many changes the chosen candidate proposed
   * @return the changed selection
   */
  public SourceSelection withChangeCount(long count) {
    return new SourceSelection(present, trials, count);
  }

  /**
   * Returns whether a change count was measured at all.
   *
   * @return whether the count is a number rather than {@link #NOT_MEASURED}
   */
  public boolean changeCountMeasured() {
    return changeCount >= 0;
  }

  /**
   * Returns what trying the given candidate cost.
   *
   * @param candidate the metamodel to look up
   * @return its trial, empty when the candidate was never tried
   */
  public Optional<Trial> trialOf(MetamodelNode candidate) {
    return trials.stream().filter(trial -> trial.candidate().equals(candidate)).findFirst();
  }

  /**
   * What trying one candidate as the dominant model cost.
   *
   * @param candidate the metamodel that was tried
   * @param changeCount the changes it proposed, or {@link SourceSelection#NOT_MEASURED} when
   *     the trial did not finish
   * @param duration how long the trial ran
   * @param failure what went wrong, empty when the trial finished
   */
  public record Trial(
      MetamodelNode candidate, long changeCount, Duration duration, Optional<String> failure) {
    /**
     * Returns the record of a trial that ran through.
     *
     * @param candidate the metamodel that was tried
     * @param changeCount how many changes it proposed
     * @param duration how long the trial ran
     * @return the finished trial
     */
    public static Trial completed(MetamodelNode candidate, long changeCount, Duration duration) {
      return new Trial(candidate, changeCount, duration, Optional.empty());
    }

    /**
     * Returns the record of a trial that did not finish.
     *
     * @param candidate the metamodel that was tried
     * @param duration how long the trial ran before it failed
     * @param reason what went wrong, of which the innermost cause is kept
     * @return the failed trial
     */
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

    /**
     * Returns whether this trial ran through.
     *
     * @return whether it recorded no failure
     */
    public boolean completed() {
      return failure.isEmpty();
    }
  }
}
