package dev.shinobi;

/**
 * Pure rules for the Sharingan dodge: tier ladder, safety caps on invulnerability, double-tap timing and direction choice.
 * The dodge is a short dash with a brief, capped damage-avoidance window. It is never permanent invulnerability:
 * the window is limited to {@link #MAX_IFRAME_TICKS} ticks and the cooldown must be at least {@link #MIN_COOLDOWN_PER_IFRAME_TICK}
 * times longer than the window, so invulnerability uptime can never exceed 1/8 (12.5%).
 */
public final class DodgeRules {
    public static final int MAX_IFRAME_TICKS = 10;
    public static final int MIN_COOLDOWN_PER_IFRAME_TICK = 8;
    public static final int MIN_COOLDOWN_TICKS = 20;
    public static final double MAX_DISTANCE = 8.0;
    public static final double MOVE_THRESHOLD = 0.04; // horizontal blocks per sample to count as "moving"

    private DodgeRules() {}

    /** One rung of the evolution ladder. Always create via {@link #tier} so the caps are applied. */
    public record Tier(String key, int cooldownTicks, int iframeTicks, double distance, double costMultiplier) {
        public double uptime() {
            return cooldownTicks <= 0 ? 1.0 : iframeTicks / (double) cooldownTicks;
        }
    }

    /** Builds a tier with every safety cap applied (config values can never break them). */
    public static Tier tier(String key, int cooldownTicks, int iframeTicks, double distance, double costMultiplier) {
        int iframes = Math.max(0, Math.min(MAX_IFRAME_TICKS, iframeTicks));
        int cooldown = Math.max(Math.max(MIN_COOLDOWN_TICKS, iframes * MIN_COOLDOWN_PER_IFRAME_TICK), cooldownTicks);
        double dist = Double.isNaN(distance) ? 3.0 : Math.max(1.0, Math.min(MAX_DISTANCE, distance));
        double cost = Double.isNaN(costMultiplier) ? 1.0 : Math.max(0.1, Math.min(3.0, costMultiplier));
        return new Tier(key, cooldown, iframes, dist, cost);
    }

    /** Which tier key applies: eternal beats mangekyo beats the tomoe count (1..3). */
    public static String stageKey(int tomoe, boolean mangekyo, boolean eternal) {
        if (eternal) return "eternal";
        if (mangekyo) return "mangekyo";
        return "tomoe" + Math.max(1, Math.min(3, tomoe));
    }

    /** Built-in defaults: each stage is a little faster/longer; Three-Tomoe widens the timing window; Eternal is cheaper. */
    public static Tier defaultTier(String key) {
        return switch (key) {
            case "tomoe1" -> tier(key, 100, 3, 3.0, 1.0);
            case "tomoe2" -> tier(key, 90, 4, 3.5, 1.0);
            case "tomoe3" -> tier(key, 80, 5, 4.0, 1.0);
            case "mangekyo" -> tier(key, 70, 5, 4.5, 1.0);
            case "eternal" -> tier(key, 60, 5, 4.5, 0.7);
            default -> tier("tomoe1", 100, 3, 3.0, 1.0);
        };
    }

    public static boolean doubleTap(long lastMs, long nowMs, long windowMs) {
        return lastMs > 0 && nowMs >= lastMs && nowMs - lastMs <= Math.max(50, windowMs);
    }

    public static boolean isInvulnerable(long nowMs, long untilMs) {
        return untilMs > 0 && nowMs <= untilMs;
    }

    /**
     * Dodge direction as a unit vector {x, z}. Uses the recent movement delta when the player is moving; otherwise falls
     * back to straight backwards from where they face (documented fallback). Yaw is Minecraft yaw in degrees.
     */
    public static double[] direction(double moveDx, double moveDz, double yawDegrees) {
        double len = Math.hypot(moveDx, moveDz);
        if (len >= MOVE_THRESHOLD && !Double.isNaN(len)) return new double[]{moveDx / len, moveDz / len};
        double yaw = Math.toRadians(yawDegrees);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        return new double[]{-fx, -fz};
    }
}
