package dev.shinobi;

import java.util.Set;

/**
 * The single place that says which of an ability's access checks apply to a player. Four kinds of access exist:
 * natural eligibility, normally unlocked progression, administrator-granted (the player holds the scroll/unlock) and
 * administrator-FORCED. Forced access is stored explicitly (never faked as natural) and is per ability or per bloodline:
 * forcing one Uchiha ability onto a Nara player does not open any other Uchiha ability.
 * Forcing only removes ACCESS checks (clan, bloodline, learned, nature, prerequisite jutsu). It never touches cooldowns,
 * chakra, sealing, world rules, PvP or destination checks, which stay in the engine and the techniques themselves.
 */
public final class AccessRules {
    public enum Source { NATURAL, FORCED_BLOODLINE, FORCED_ABILITY }

    public record Input(String abilityId, String abilityBloodline, Set<String> forcedAbilities, Set<String> forcedBloodlines) {}

    public record Decision(Source source, boolean skipClan, boolean skipBloodline, boolean skipKnown,
                           boolean skipNature, boolean skipProgression) {
        public boolean forced() {
            return source != Source.NATURAL;
        }
    }

    private static final Decision NATURAL = new Decision(Source.NATURAL, false, false, false, false, false);

    private AccessRules() {}

    public static Decision decide(Input in) {
        if (in.abilityId() != null && in.forcedAbilities().contains(in.abilityId())) {
            return new Decision(Source.FORCED_ABILITY, true, true, true, true, true);
        }
        if (in.abilityBloodline() != null && in.forcedBloodlines().contains(in.abilityBloodline())) {
            return new Decision(Source.FORCED_BLOODLINE, true, true, false, false, false);
        }
        return NATURAL;
    }
}
