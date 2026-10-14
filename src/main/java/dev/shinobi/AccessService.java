package dev.shinobi;

import java.util.ArrayList;
import java.util.List;

/** Bukkit-facing wrapper around {@link AccessRules}: builds the input from the profile and explains decisions. */
public final class AccessService {
    private final ShinobiPlugin plugin;

    public AccessService(ShinobiPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean forcedEnabled() {
        return plugin.getConfig().getBoolean("admin.forced-grants-enabled", true);
    }

    public AccessRules.Decision decide(Profile pr, Jutsu j) {
        if (!forcedEnabled()) {
            return AccessRules.decide(new AccessRules.Input(j.id, j.bloodline, java.util.Set.of(), java.util.Set.of()));
        }
        return AccessRules.decide(new AccessRules.Input(j.id, j.bloodline, pr.forcedAbilities, pr.forcedBloodlines));
    }

    /** Human-readable reasons why an ability is allowed or denied for this profile (for /shinobi admin access). */
    public List<String> explain(Profile pr, Jutsu j) {
        List<String> out = new ArrayList<>();
        AccessRules.Decision d = decide(pr, j);
        out.add("Ability: " + j.name + " (" + j.id + ")" + (j.clan != null ? ", clan " + j.clan.display : "")
                + (j.bloodline != null ? ", bloodline " + j.bloodline : ""));
        out.add("Access source: " + d.source() + (forcedEnabled() ? "" : " (forced grants are disabled in config)"));
        if (j.clan != null) out.add("Clan: " + (d.skipClan() ? "skipped (forced)" : (pr.clan == j.clan && pr.awakened ? "ok" : "DENIED (needs awakened " + j.clan.display + ")")));
        if (j.bloodline != null) {
            BloodlineRules.Result r = plugin.bloodlines().check(pr, j.bloodline);
            out.add("Bloodline: " + (d.skipBloodline() ? "skipped (forced)" : r + " - " + BloodlineRules.describe(r)));
        }
        if (j.needsKnown) out.add("Learned: " + (d.skipKnown() ? "skipped (forced)" : (plugin.casting().unlocked(pr, j) ? "yes" : "NO")));
        if (j.requireJutsu != null) out.add("Prerequisite " + j.requireJutsu + ": " + (d.skipProgression() ? "skipped (forced)" : pr.masteryOf(j.requireJutsu) + "/" + j.requireCasts));
        if (j.needsDojutsu) out.add("Dojutsu active: " + (pr.dojutsuActive ? "yes" : "NO") + " (never skipped by force)");
        out.add("Still enforced for every cast: chakra, cooldown" + (plugin.getConfig().getBoolean("admin.forced-abilities-respect-cooldowns", true) ? "" : " (cooldown ignored for forced abilities by config)")
                + ", sealing, world rules, PvP and destination checks.");
        return out;
    }
}
