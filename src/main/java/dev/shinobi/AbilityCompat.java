package dev.shinobi;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Explicit ability-compatibility rules. Nothing is blocked by default: only ability ids listed as incompatible with an
 * active state (e.g. Susanoo) are refused while that state is on. Everything else keeps its own cost, cooldown and permissions.
 */
public final class AbilityCompat {
    /** Default for {@code susanoo.incompatible-abilities}: Kamui's self-phase would leave the Susanoo body behind. */
    public static final List<String> SUSANOO_DEFAULT = List.of("kamui");

    private AbilityCompat() {}

    public static Set<String> normalise(List<String> ids) {
        Set<String> out = new HashSet<>();
        if (ids == null) return out;
        for (String s : ids) {
            String id = AdminInput.id(s == null ? null : s.trim());
            if (id != null) out.add(id);
        }
        return out;
    }

    /** True if {@code abilityId} must be refused while the state is active. Inactive state never blocks anything. */
    public static boolean blocked(boolean stateActive, String abilityId, Set<String> incompatible) {
        return stateActive && abilityId != null && incompatible.contains(abilityId.toLowerCase(Locale.ROOT));
    }
}
