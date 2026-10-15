package dev.shinobi;

/** Tests for the pure Sharingan dodge rules. */
public final class DodgeLogicTests {
    private static int run, failed;

    private static void check(String name, boolean ok) {
        run++;
        if (!ok) { failed++; System.out.println("FAIL: " + name); }
    }

    private static boolean near(double a, double b) { return Math.abs(a - b) < 1e-9; }

    public static void main(String[] args) {
        String[] keys = {"tomoe1", "tomoe2", "tomoe3", "mangekyo", "eternal"};

        // --- evolution ladder ---
        DodgeRules.Tier prev = null;
        for (String k : keys) {
            DodgeRules.Tier t = DodgeRules.defaultTier(k);
            check(k + " key matches", t.key().equals(k));
            check(k + " uptime capped at 12.5%", t.uptime() <= 1.0 / DodgeRules.MIN_COOLDOWN_PER_IFRAME_TICK + 1e-9);
            check(k + " has a real cooldown", t.cooldownTicks() >= DodgeRules.MIN_COOLDOWN_TICKS);
            check(k + " has a short, bounded window", t.iframeTicks() >= 1 && t.iframeTicks() <= DodgeRules.MAX_IFRAME_TICKS);
            if (prev != null) {
                check(k + " never has a longer cooldown than the previous stage", t.cooldownTicks() <= prev.cooldownTicks());
                check(k + " never dashes shorter than the previous stage", t.distance() >= prev.distance());
                check(k + " never has a smaller window than the previous stage", t.iframeTicks() >= prev.iframeTicks());
            }
            prev = t;
        }
        check("three-tomoe widens the timing window over one-tomoe", DodgeRules.defaultTier("tomoe3").iframeTicks() > DodgeRules.defaultTier("tomoe1").iframeTicks());
        check("only eternal is cheaper", DodgeRules.defaultTier("eternal").costMultiplier() < 1.0 && DodgeRules.defaultTier("mangekyo").costMultiplier() == 1.0);
        check("unknown key falls back to tomoe1", DodgeRules.defaultTier("bogus").key().equals("tomoe1"));

        // --- config can never break the caps ---
        DodgeRules.Tier evil = DodgeRules.tier("x", 1, 9999, 9999, 0);
        check("iframes clamped", evil.iframeTicks() == DodgeRules.MAX_IFRAME_TICKS);
        check("cooldown forced above iframe*ratio", evil.cooldownTicks() >= DodgeRules.MAX_IFRAME_TICKS * DodgeRules.MIN_COOLDOWN_PER_IFRAME_TICK);
        check("distance clamped", near(evil.distance(), DodgeRules.MAX_DISTANCE));
        check("cost multiplier floor", evil.costMultiplier() >= 0.1);
        check("uptime still capped for hostile config", evil.uptime() <= 0.125 + 1e-9);
        DodgeRules.Tier nan = DodgeRules.tier("x", 0, -5, Double.NaN, Double.NaN);
        check("negative iframes become zero", nan.iframeTicks() == 0);
        check("NaN distance and cost sanitised", nan.distance() >= 1.0 && nan.costMultiplier() > 0);
        check("zero iframes still has minimum cooldown", nan.cooldownTicks() >= DodgeRules.MIN_COOLDOWN_TICKS);

        // --- stage selection ---
        check("tomoe count picks the key", DodgeRules.stageKey(2, false, false).equals("tomoe2"));
        check("tomoe clamped low", DodgeRules.stageKey(0, false, false).equals("tomoe1"));
        check("tomoe clamped high", DodgeRules.stageKey(9, false, false).equals("tomoe3"));
        check("mangekyo beats tomoe", DodgeRules.stageKey(3, true, false).equals("mangekyo"));
        check("eternal beats mangekyo", DodgeRules.stageKey(3, true, true).equals("eternal"));

        // --- repeated-dodge abuse: simulate spamming the key for 60 seconds, cooldown enforced like the engine ---
        for (String k : keys) {
            DodgeRules.Tier t = DodgeRules.defaultTier(k);
            long cdMs = t.cooldownTicks() * 50L, windowMs = t.iframeTicks() * 50L;
            long next = 0, invulMs = 0;
            for (long now = 0; now < 60_000; now += 50) {
                if (now >= next) { next = now + cdMs; invulMs += windowMs; }
            }
            check(k + ": 60s of spamming gives <= 12.5% invulnerability (+1 window)", invulMs <= 60_000 * 0.125 + windowMs);
        }

        // --- double-tap timing ---
        check("double tap inside window", DodgeRules.doubleTap(1000, 1250, 300));
        check("double tap outside window", !DodgeRules.doubleTap(1000, 1400, 300));
        check("no previous tap", !DodgeRules.doubleTap(0, 1000, 300));
        check("clock going backwards is not a double tap", !DodgeRules.doubleTap(2000, 1500, 300));
        check("window has a floor", DodgeRules.doubleTap(1000, 1040, 0));

        // --- invulnerability window ---
        check("inside window", DodgeRules.isInvulnerable(1100, 1200));
        check("at the edge", DodgeRules.isInvulnerable(1200, 1200));
        check("after window", !DodgeRules.isInvulnerable(1201, 1200));
        check("never dodged", !DodgeRules.isInvulnerable(1000, 0));

        // --- direction ---
        double[] d = DodgeRules.direction(3, 4, 123);
        check("moving: uses movement, normalised", near(d[0], 0.6) && near(d[1], 0.8));
        double[] back = DodgeRules.direction(0, 0, 0);       // facing +Z (yaw 0) -> back is -Z
        check("standing still: backwards from facing (yaw 0)", near(back[0], 0) && near(back[1], -1));
        double[] back90 = DodgeRules.direction(0.01, 0, 90); // yaw 90 faces -X -> back is +X; tiny motion ignored
        check("standing still: backwards from facing (yaw 90)", near(back90[0], 1) && Math.abs(back90[1]) < 1e-9);
        double[] nanDir = DodgeRules.direction(Double.NaN, 0, 0);
        check("NaN movement falls back safely", near(Math.hypot(nanDir[0], nanDir[1]), 1.0));
        for (double yaw = -360; yaw <= 360; yaw += 17) {
            double[] v = DodgeRules.direction(0, 0, yaw);
            if (Math.abs(Math.hypot(v[0], v[1]) - 1.0) > 1e-9) { check("fallback direction is a unit vector at yaw " + yaw, false); break; }
        }

        System.out.println(run + " checks, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
