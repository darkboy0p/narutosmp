package dev.shinobi;

import java.util.Set;

/**
 * Pure eligibility rules. This is the single decision used by every activation path (the engine runs it for every cast,
 * the dojutsu toggle runs it too), so commands, GUI, quick-cast and saved slots cannot bypass it.
 */
public final class BloodlineRules {
    private BloodlineRules() {}

    public enum Result { OK, UNKNOWN, DISABLED, WRONG_CLAN, NOT_AWAKENED, MISSING_PARENT, NOT_UNLOCKED }

    /**
     * @param unlocked bloodline ids the player has been granted / has earned (not including innate ones)
     */
    public static Result check(BloodlineRegistry reg, String id, Clan clan, boolean awakened, Set<String> unlocked) {
        return check(reg, id, clan, awakened, unlocked, 0);
    }

    private static Result check(BloodlineRegistry reg, String id, Clan clan, boolean awakened, Set<String> unlocked, int depth) {
        if (depth > 16) return Result.MISSING_PARENT; // defensive: a looping parent chain never grants anything
        BloodlineDef d = reg.get(id);
        if (d == null) return Result.UNKNOWN;
        if (!d.enabled) return Result.DISABLED;
        if (clan == null || (!d.anyClan && !d.eligibleClans.contains(clan))) return Result.WRONG_CLAN;
        if (!awakened) return Result.NOT_AWAKENED;
        if (d.parent != null && check(reg, d.parent, clan, awakened, unlocked, depth + 1) != Result.OK) return Result.MISSING_PARENT;
        if (!d.innate && !unlocked.contains(d.id)) return Result.NOT_UNLOCKED;
        return Result.OK;
    }

    public static String describe(Result r) {
        return switch (r) {
            case OK -> "allowed";
            case UNKNOWN -> "unknown bloodline";
            case DISABLED -> "this bloodline is disabled on this server";
            case WRONG_CLAN -> "your clan cannot use this bloodline";
            case NOT_AWAKENED -> "you have not awakened your clan";
            case MISSING_PARENT -> "you have not reached the earlier stage of this bloodline";
            case NOT_UNLOCKED -> "you have not unlocked this bloodline";
        };
    }
}
