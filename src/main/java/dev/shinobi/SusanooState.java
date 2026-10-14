package dev.shinobi;

/**
 * Pure Susanoo state machine: stages, per-second upkeep and protection level. It knows nothing about other abilities:
 * Susanoo never touches chakra reservations, cooldowns or charging sessions (see SusanooLogicTests).
 */
public final class SusanooState {
    public static final int MAX_STAGE = 4;
    public static final String[] STAGE_NAMES = {"", "Ribcage", "Skeleton", "Armored", "Complete"};

    public enum Advance { STARTED, ADVANCED, DISMISSED }

    public record Upkeep(double chakra, double strainGain, boolean collapsed) {}

    private int stage;

    public int stage() {
        return stage;
    }

    public boolean active() {
        return stage > 0;
    }

    /** First cast starts stage 1, each further cast adds a stage, a cast at the last stage dismisses it. */
    public Advance advance() {
        if (stage >= MAX_STAGE) {
            stage = 0;
            return Advance.DISMISSED;
        }
        stage++;
        return stage == 1 ? Advance.STARTED : Advance.ADVANCED;
    }

    public void end() {
        stage = 0;
    }

    /** Chakra drained per second at a stage. Higher stages cost MORE, never less (no free power at the top). */
    public static double drainPerSecond(int stage, double perStage) {
        return Math.max(0, stage) * Math.max(0.0, perStage);
    }

    public static double strainPerSecond(int stage, boolean eternal) {
        return eternal ? 0.0 : Math.max(0, stage);
    }

    /** Potion amplifier for the protection buff: stage 1 -> 0, stage 4 -> 3. Capped so it can never become invulnerability. */
    public static int resistanceAmplifier(int stage) {
        return Math.max(0, Math.min(3, stage - 1));
    }

    /** One second of upkeep. Chakra never goes negative; reaching zero collapses Susanoo. */
    public Upkeep upkeep(double chakra, boolean eternal, double drainPerStage) {
        double left = Math.max(0.0, chakra - drainPerSecond(stage, drainPerStage));
        return new Upkeep(left, strainPerSecond(stage, eternal), left <= 0.0);
    }
}
