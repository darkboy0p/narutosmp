package dev.shinobi;

/**
 * Pure (Bukkit-free) description of how a technique is charged, plus the rules that decide what a release, a
 * cancel or a tick means at a given charge time. Everything here is deterministic so it can be unit-tested.
 *
 * <p>Time is measured in server ticks. Charge stages: stage 0 = still gathering (below {@code minTicks});
 * stages 1..stages-1 = castable and growing; stage {@code stages} = fully charged (at or above {@code maxTicks}).
 */
public final class ChargeSpec {
    public enum EarlyRelease {
        /** Releasing before the minimum charge aborts the charge (refund per config). */
        FIZZLE,
        /** Releasing before the minimum charge is ignored; the charge simply continues. */
        IGNORE
    }

    public enum Decision { IGNORE, FIZZLE, RELEASE }

    public enum Tick { CONTINUE, AUTO_RELEASE, AUTO_CANCEL }

    public final String style;          // visual/sound style key, e.g. "lightning"
    public final int minTicks;
    public final int maxTicks;
    public final int stages;            // 2..6
    public final int holdLimitTicks;    // how long a full charge may be held before the overcharge action fires
    public final int graceTicks;        // releases/cancels this soon after starting are ignored (accidental clicks)
    public final int slownessAmp;       // -1 = no movement restriction
    public final boolean cancellable;
    public final EarlyRelease earlyRelease;
    public final double minScale;       // damage multiplier at minimum charge
    public final double maxScale;       // damage multiplier at full charge
    public final double rangeMinScale;
    public final double rangeMaxScale;

    private ChargeSpec(Builder b) {
        this.style = b.style;
        this.minTicks = Math.max(1, b.minTicks);
        this.maxTicks = Math.max(this.minTicks + 1, b.maxTicks);
        this.stages = Math.max(2, Math.min(6, b.stages));
        this.holdLimitTicks = Math.max(20, b.holdLimitTicks);
        this.graceTicks = Math.max(0, Math.min(this.minTicks, b.graceTicks));
        this.slownessAmp = Math.max(-1, Math.min(4, b.slownessAmp));
        this.cancellable = b.cancellable;
        this.earlyRelease = b.earlyRelease;
        this.minScale = nonNegative(b.minScale);
        this.maxScale = Math.max(this.minScale, nonNegative(b.maxScale));
        this.rangeMinScale = nonNegative(b.rangeMinScale);
        this.rangeMaxScale = Math.max(this.rangeMinScale, nonNegative(b.rangeMaxScale));
    }

    private static double nonNegative(double d) {
        return Double.isNaN(d) || d < 0 ? 0 : d;
    }

    public boolean ready(int elapsed) {
        return elapsed >= minTicks;
    }

    public boolean full(int elapsed) {
        return elapsed >= maxTicks;
    }

    /** 0..1 position between minimum and full charge (0 below the minimum). */
    public double progress(int elapsed) {
        if (elapsed <= minTicks) return 0.0;
        if (elapsed >= maxTicks) return 1.0;
        return (elapsed - minTicks) / (double) (maxTicks - minTicks);
    }

    /** 0..1 position between zero and full charge (used for visual intensity). */
    public double fraction(int elapsed) {
        if (elapsed <= 0) return 0.0;
        if (elapsed >= maxTicks) return 1.0;
        return elapsed / (double) maxTicks;
    }

    public int stageOf(int elapsed) {
        if (elapsed < minTicks) return 0;
        if (elapsed >= maxTicks) return stages;
        return 1 + (int) (progress(elapsed) * (stages - 1));
    }

    public double damageScale(int elapsed) {
        return minScale + (maxScale - minScale) * progress(elapsed);
    }

    public double rangeScale(int elapsed) {
        return rangeMinScale + (rangeMaxScale - rangeMinScale) * progress(elapsed);
    }

    /** What a release action means right now. */
    public Decision decideRelease(int elapsed) {
        if (elapsed < graceTicks) return Decision.IGNORE;
        if (elapsed < minTicks) return earlyRelease == EarlyRelease.IGNORE ? Decision.IGNORE : Decision.FIZZLE;
        return Decision.RELEASE;
    }

    /** What the periodic tick should do. A charge can never be held longer than maxTicks + holdLimitTicks. */
    public Tick onTick(int elapsed, boolean autoReleaseOnTimeout) {
        if (elapsed >= maxTicks + holdLimitTicks) return autoReleaseOnTimeout ? Tick.AUTO_RELEASE : Tick.AUTO_CANCEL;
        return Tick.CONTINUE;
    }

    public static Builder builder(String style, int minTicks, int maxTicks) {
        return new Builder(style, minTicks, maxTicks);
    }

    public Builder toBuilder() {
        Builder b = new Builder(style, minTicks, maxTicks);
        b.stages = stages;
        b.holdLimitTicks = holdLimitTicks;
        b.graceTicks = graceTicks;
        b.slownessAmp = slownessAmp;
        b.cancellable = cancellable;
        b.earlyRelease = earlyRelease;
        b.minScale = minScale;
        b.maxScale = maxScale;
        b.rangeMinScale = rangeMinScale;
        b.rangeMaxScale = rangeMaxScale;
        return b;
    }

    public static final class Builder {
        private String style;
        private int minTicks;
        private int maxTicks;
        private int stages = 3;
        private int holdLimitTicks = 60;
        private int graceTicks = 6;
        private int slownessAmp = -1;
        private boolean cancellable = true;
        private EarlyRelease earlyRelease = EarlyRelease.FIZZLE;
        private double minScale = 1.0;
        private double maxScale = 1.0;
        private double rangeMinScale = 1.0;
        private double rangeMaxScale = 1.0;

        private Builder(String style, int minTicks, int maxTicks) {
            this.style = style;
            this.minTicks = minTicks;
            this.maxTicks = maxTicks;
        }

        public Builder style(String s) { this.style = s; return this; }
        public Builder minTicks(int v) { this.minTicks = v; return this; }
        public Builder maxTicks(int v) { this.maxTicks = v; return this; }
        public Builder stages(int v) { this.stages = v; return this; }
        public Builder holdLimit(int v) { this.holdLimitTicks = v; return this; }
        public Builder grace(int v) { this.graceTicks = v; return this; }
        public Builder slowness(int amp) { this.slownessAmp = amp; return this; }
        public Builder cancellable(boolean v) { this.cancellable = v; return this; }
        public Builder earlyRelease(EarlyRelease v) { this.earlyRelease = v; return this; }
        public Builder damageScale(double min, double max) { this.minScale = min; this.maxScale = max; return this; }
        public Builder rangeScale(double min, double max) { this.rangeMinScale = min; this.rangeMaxScale = max; return this; }
        public ChargeSpec build() { return new ChargeSpec(this); }
    }
}
