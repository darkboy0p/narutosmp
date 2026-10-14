package dev.shinobi;

import java.util.*;

/**
 * Pure (no Bukkit) Sharingan progression rules: stage ladder, explicit awakening lifecycle, anti-farm credit,
 * Mangekyo eligibility, persistent unique-ability assignment and Eternal Mangekyo prerequisites.
 * Separate concepts, separate fields: clan, stage, awakening state, assignment and grade are never merged.
 */
public final class DojutsuProgression {
    private DojutsuProgression() {}

    /** Evolution ladder. RINNEGAN is optional and only reachable when the server enables it. */
    public enum Stage {
        DORMANT, ONE, TWO, THREE, MANGEKYO, ETERNAL, RINNEGAN;
        public Stage next() { return this == RINNEGAN ? null : values()[ordinal() + 1]; }
        public static Stage parse(String s) {
            if (s == null) return DORMANT;
            try { return valueOf(s.trim().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return DORMANT; }
        }
    }

    /** DORMANT -> ELIGIBLE -> IN_PROGRESS -> AWAKENED, for the Mangekyo (and other) awakenings. */
    public enum Awakening { DORMANT, ELIGIBLE, IN_PROGRESS, AWAKENED;
        public static Awakening parse(String s) {
            if (s == null) return DORMANT;
            try { return valueOf(s.trim().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return DORMANT; }
        }
    }
    public enum Event { BECOME_ELIGIBLE, BEGIN, COMPLETE, INTERRUPT, ADMIN_RESET }

    public record Result(boolean ok, String reason) {
        public static Result yes() { return new Result(true, ""); }
        public static Result no(String r) { return new Result(false, r); }
    }

    /** Requirement to ENTER a stage (all configurable). */
    public record Req(long progress, int missions, int minGradeIndex, boolean challenge) {}
    /** What the player currently has (read from real data by the caller). */
    public record Status(long progress, int missions, int gradeIndex, boolean challengeDone) {}

    /** Can the player move from current to target? Never skips a stage, never repeats, never enters a disabled stage. */
    public static Result canAdvance(Stage current, Stage target, Set<Stage> enabled, Map<Stage, Req> reqs, Status s, boolean uchiha) {
        if (!uchiha && target.ordinal() <= Stage.ETERNAL.ordinal()) return Result.no("Sharingan stages require Uchiha eligibility");
        if (current.next() != target) return Result.no("cannot skip stages (next is " + current.next() + ")");
        if (!enabled.contains(target)) return Result.no(target + " is disabled on this server");
        Req r = reqs.get(target);
        if (r == null) return Result.no("no requirements configured for " + target);
        if (s.progress() < r.progress()) return Result.no("needs " + (r.progress() - s.progress()) + " more progress");
        if (s.missions() < r.missions()) return Result.no("needs " + (r.missions() - s.missions()) + " more missions");
        if (s.gradeIndex() < r.minGradeIndex()) return Result.no("SMP grade too low");
        if (r.challenge() && !s.challengeDone()) return Result.no("the stage challenge is not complete");
        return Result.yes();
    }

    /** Stage requirements must be non-negative and never decrease up the ladder. */
    public static List<String> validate(Map<Stage, Req> reqs) {
        List<String> e = new ArrayList<>();
        long p = 0; int m = 0;
        for (Stage st : Stage.values()) {
            Req r = reqs.get(st);
            if (r == null) continue;
            if (r.progress() < 0 || r.missions() < 0 || r.minGradeIndex() < 0) e.add("negative requirement at " + st);
            if (r.progress() < p || r.missions() < m) e.add("requirements decrease at " + st);
            p = Math.max(p, r.progress()); m = Math.max(m, r.missions());
        }
        return e;
    }

    /**
     * Anti-farm progress credit. Self-targets and dummies-in-a-loop earn nothing; a per-cooldown gate blocks rapid repeats;
     * repeated hits on the same target within the window diminish linearly to zero at {@code perTargetCap}.
     */
    public static int credit(int base, boolean selfTarget, boolean validTarget, long nowMs, long lastCreditMs, long cooldownMs, int hitsOnTargetInWindow, int perTargetCap) {
        if (base <= 0 || selfTarget || !validTarget) return 0;
        if (nowMs - lastCreditMs < cooldownMs) return 0;
        if (perTargetCap <= 0 || hitsOnTargetInWindow >= perTargetCap) return 0;
        double f = 1.0 - (double) hitsOnTargetInWindow / perTargetCap;
        return (int) Math.max(0, Math.floor(base * f));
    }

    /** Awakening lifecycle. Invalid transitions return the unchanged state (caller checks via {@link #allowed}). */
    public static boolean allowed(Awakening cur, Event ev) {
        return switch (ev) {
            case BECOME_ELIGIBLE -> cur == Awakening.DORMANT;
            case BEGIN -> cur == Awakening.ELIGIBLE;
            case COMPLETE -> cur == Awakening.IN_PROGRESS;
            case INTERRUPT -> cur == Awakening.IN_PROGRESS;
            case ADMIN_RESET -> true;
        };
    }
    public static Awakening apply(Awakening cur, Event ev) {
        if (!allowed(cur, ev)) return cur;
        return switch (ev) {
            case BECOME_ELIGIBLE -> Awakening.ELIGIBLE;
            case BEGIN -> Awakening.IN_PROGRESS;
            case COMPLETE -> Awakening.AWAKENED;
            case INTERRUPT -> Awakening.ELIGIBLE;
            case ADMIN_RESET -> Awakening.DORMANT;
        };
    }
    /** On login/restart an awakening that was IN_PROGRESS has no running sequence: safely fall back to ELIGIBLE (never AWAKENED). */
    public static Awakening recoverOnLoad(Awakening saved) {
        return saved == Awakening.IN_PROGRESS ? Awakening.ELIGIBLE : saved;
    }

    /** Mangekyo awakening may begin only for an Uchiha with Three Tomoe, ELIGIBLE state, enabled stage and the milestone done. */
    public static Result canBeginMangekyo(boolean uchiha, Stage stage, Awakening aw, boolean mangekyoEnabled, boolean milestoneDone) {
        if (!uchiha) return Result.no("Uchiha eligibility required");
        if (!mangekyoEnabled) return Result.no("Mangekyo Sharingan is disabled");
        if (stage != Stage.THREE) return Result.no("requires Three Tomoe Sharingan");
        if (aw == Awakening.AWAKENED) return Result.no("already awakened");
        if (aw == Awakening.IN_PROGRESS) return Result.no("awakening already in progress");
        if (aw != Awakening.ELIGIBLE) return Result.no("awakening not yet available");
        if (!milestoneDone) return Result.no("the awakening milestone is not complete");
        return Result.yes();
    }

    /**
     * Unique ability assignment. An existing assignment is ALWAYS returned (no reroll on login/death/restart).
     * A new one is chosen only from enabled, not-yet-held pool entries; {@code roll} is a 0..1 random supplied by the caller.
     * Returns null when the pool offers nothing.
     */
    public static String assign(List<String> pool, Set<String> disabled, Collection<String> held, double roll) {
        if (held != null && !held.isEmpty()) return held.iterator().next();
        List<String> ok = new ArrayList<>();
        for (String a : pool) if (!disabled.contains(a) && !ok.contains(a)) ok.add(a);
        if (ok.isEmpty()) return null;
        int i = (int) Math.min(ok.size() - 1, Math.max(0, Math.floor(roll * ok.size())));
        return ok.get(i);
    }
    /** Reroll/reset of an assignment needs explicit authorisation (admin) or a server rule that allows rerolls. */
    public static Result canReassign(boolean authorizedAdmin, boolean serverAllowsReroll, boolean hasAssignment) {
        if (!hasAssignment) return Result.yes();
        return (authorizedAdmin || serverAllowsReroll) ? Result.yes() : Result.no("assignment is permanent");
    }

    /**
     * Eternal Mangekyo: needs an awakened Mangekyo plus configured prerequisites. Routes (quest, material, mastery) are
     * alternatives when {@code anyRoute} is true, otherwise all configured routes are needed. No PvP/harm route is required.
     */
    public static Result canEvolveEternal(Stage stage, Awakening aw, boolean enabled, boolean anyRoute, boolean[] routeDone, boolean[] routeConfigured) {
        if (!enabled) return Result.no("Eternal Mangekyo is disabled");
        if (stage != Stage.MANGEKYO || aw != Awakening.AWAKENED) return Result.no("requires an awakened Mangekyo Sharingan");
        int configured = 0, done = 0;
        for (int i = 0; i < routeConfigured.length; i++) if (routeConfigured[i]) { configured++; if (routeDone[i]) done++; }
        if (configured == 0) return Result.no("no evolution route is configured");
        boolean ok = anyRoute ? done > 0 : done == configured;
        return ok ? Result.yes() : Result.no(anyRoute ? "complete one evolution route" : "complete every evolution route");
    }
}
