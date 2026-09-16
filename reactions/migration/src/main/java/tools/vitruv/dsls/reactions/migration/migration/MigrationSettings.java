package tools.vitruv.dsls.reactions.migration.migration;

import tools.vitruv.dsls.reactions.migration.preservation.PreservationPolicy;

/**
 * How a migration is to be carried out, apart from where the V-SUM and the rule set lie.
 *
 * @param updateSources whether a full migration carries information that only the old derived
 *     models held back into the dominant one
 * @param maxSourceUpdateRounds how often that may be repeated before the update gives up
 * @param mode whether every rule is replayed or only the changed ones are followed
 * @param preservation how far content that no rule produced is carried over
 * @param askOnAmbiguity whether an ambiguous placement may be put to the caller
 */
public record MigrationSettings(
    boolean updateSources,
    int maxSourceUpdateRounds,
    MigrationMode mode,
    PreservationPolicy preservation,
    boolean askOnAmbiguity) {
  /**
   * Returns the settings of a full migration that updates its sources and preserves the content
   * a person wrote, without asking anybody about an ambiguous placement.
   *
   * @return the default settings
   */
  public static MigrationSettings defaults() {
    return new MigrationSettings(true, 3, MigrationMode.FULL, PreservationPolicy.USER, false);
  }

  /**
   * Returns the settings of a full migration that leaves the dominant model as it is.
   *
   * @return the default settings without a source update
   */
  public static MigrationSettings forwardOnly() {
    return new MigrationSettings(false, 0, MigrationMode.FULL, PreservationPolicy.USER, false);
  }

  /**
   * Returns the same settings under another migration mode.
   *
   * @param newMode the mode to run in
   * @return the changed settings
   */
  public MigrationSettings withMode(MigrationMode newMode) {
    return new MigrationSettings(
        updateSources, maxSourceUpdateRounds, newMode, preservation, askOnAmbiguity);
  }

  /**
   * Returns the same settings under another preservation policy.
   *
   * @param policy the policy to apply
   * @return the changed settings
   */
  public MigrationSettings withPreservation(PreservationPolicy policy) {
    return new MigrationSettings(
        updateSources, maxSourceUpdateRounds, mode, policy, askOnAmbiguity);
  }

  /**
   * Returns the same settings with asking switched on or off.
   *
   * @param asking whether an ambiguous placement may be put to the caller
   * @return the changed settings
   */
  public MigrationSettings withAsking(boolean asking) {
    return new MigrationSettings(updateSources, maxSourceUpdateRounds, mode, preservation, asking);
  }
}
