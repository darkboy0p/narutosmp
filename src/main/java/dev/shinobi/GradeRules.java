package dev.shinobi;

import java.util.*;

/**
 * Pure SMP-grade rules (no Bukkit). Grades are an ordered, configurable ladder. This is separate from
 * PvP tier ratings, clan identity, bloodlines and ability mastery: a grade never unlocks a power by itself.
 */
public final class GradeRules {
    private GradeRules() {}

    /** One rung. minXp/minMissions/minTraining are thresholds; adminApproval means automatic promotion is blocked. */
    public record Grade(String id, String name, boolean enabled, long minXp, int minMissions, int minTraining, boolean adminApproval) {}

    /** A player's progression numbers relevant to grading (read from the real progression data by the caller). */
    public record Stats(long xp, int missions, int training, boolean approved) {}

    public record Result(boolean ok, String reason) {
        static Result yes() { return new Result(true, ""); }
        static Result no(String r) { return new Result(false, r); }
    }

    /** Validate a ladder: non-empty, unique lowercase ids, non-negative thresholds, thresholds never decrease up the ladder. */
    public static List<String> validate(List<Grade> ladder) {
        List<String> errors = new ArrayList<>();
        if (ladder == null || ladder.isEmpty()) { errors.add("no grades configured"); return errors; }
        Set<String> seen = new HashSet<>();
        Grade prev = null;
        for (Grade g : ladder) {
            if (g.id() == null || !g.id().matches("[a-z0-9_]+")) errors.add("invalid id: " + g.id());
            else if (!seen.add(g.id())) errors.add("duplicate id: " + g.id());
            if (g.minXp() < 0 || g.minMissions() < 0 || g.minTraining() < 0) errors.add("negative threshold: " + g.id());
            if (g.enabled()) {
                if (prev != null && (g.minXp() < prev.minXp() || g.minMissions() < prev.minMissions() || g.minTraining() < prev.minTraining()))
                    errors.add("requirements decrease at " + g.id());
                prev = g;
            }
        }
        if (enabled(ladder).isEmpty()) errors.add("all grades disabled");
        return errors;
    }

    public static List<Grade> enabled(List<Grade> ladder) {
        List<Grade> out = new ArrayList<>();
        for (Grade g : ladder) if (g.enabled()) out.add(g);
        return out;
    }

    /** Index of the grade within the enabled ladder, or -1 when unknown/disabled (e.g. an owner disabled it after the player earned it). */
    public static int indexOf(List<Grade> ladder, String id) {
        List<Grade> e = enabled(ladder);
        for (int i = 0; i < e.size(); i++) if (e.get(i).id().equals(id)) return i;
        return -1;
    }

    /** The grade a player starts at: the first enabled rung. */
    public static String starting(List<Grade> ladder) { return enabled(ladder).get(0).id(); }

    /** Can the player be promoted to the NEXT enabled grade? Never skips a rung, never promotes past the top, never twice. */
    public static Result canPromote(List<Grade> ladder, String currentId, Stats s) {
        List<Grade> e = enabled(ladder);
        int i = indexOf(ladder, currentId);
        if (i < 0) return Result.no("current grade is not on the enabled ladder");
        if (i + 1 >= e.size()) return Result.no("already at the highest grade");
        Grade next = e.get(i + 1);
        if (next.adminApproval() && !s.approved()) return Result.no(next.name() + " requires administrator approval");
        if (s.xp() < next.minXp()) return Result.no("needs " + (next.minXp() - s.xp()) + " more XP");
        if (s.missions() < next.minMissions()) return Result.no("needs " + (next.minMissions() - s.missions()) + " more missions");
        if (s.training() < next.minTraining()) return Result.no("needs " + (next.minTraining() - s.training()) + " more training milestones");
        return Result.yes();
    }

    /** Progress toward the next grade as 0..1 (the lowest of the configured requirement ratios); 1 at the top rung. */
    public static double progress(List<Grade> ladder, String currentId, Stats s) {
        List<Grade> e = enabled(ladder);
        int i = indexOf(ladder, currentId);
        if (i < 0) return 0;
        if (i + 1 >= e.size()) return 1;
        Grade n = e.get(i + 1);
        double r = 1;
        if (n.minXp() > 0) r = Math.min(r, (double) s.xp() / n.minXp());
        if (n.minMissions() > 0) r = Math.min(r, (double) s.missions() / n.minMissions());
        if (n.minTraining() > 0) r = Math.min(r, (double) s.training() / n.minTraining());
        return Math.max(0, Math.min(1, r));
    }

    /** Admin set: any enabled grade id is allowed; unknown or disabled ids are rejected. */
    public static Result canSet(List<Grade> ladder, String targetId) {
        return indexOf(ladder, targetId) >= 0 ? Result.yes() : Result.no("unknown or disabled grade: " + targetId);
    }

    /** Admin demote target: previous enabled rung, or null at the bottom. */
    public static String demoteTarget(List<Grade> ladder, String currentId) {
        int i = indexOf(ladder, currentId);
        return i <= 0 ? null : enabled(ladder).get(i - 1).id();
    }

    /** Suggested default ladder; server owners may rename, reorder, enable or disable. Not mandatory. */
    public static List<Grade> defaults() {
        return List.of(
            new Grade("academy", "Academy Student", true, 0, 0, 0, false),
            new Grade("genin", "Genin", true, 100, 3, 1, false),
            new Grade("chunin", "Chūnin", true, 500, 10, 3, false),
            new Grade("jonin", "Jōnin", true, 1500, 25, 6, false),
            new Grade("special_jonin", "Special Jōnin", true, 2500, 40, 8, true),
            new Grade("anbu", "ANBU", true, 4000, 60, 10, true),
            new Grade("kage", "Kage", true, 8000, 100, 15, true));
    }
}
