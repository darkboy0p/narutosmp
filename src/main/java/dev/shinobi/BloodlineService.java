package dev.shinobi;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Server-side bloodline authority: loads definitions (code defaults + config overrides) and answers eligibility
 * questions for a profile. Every activation path asks this class; there is no client-side or GUI-side decision.
 */
public final class BloodlineService {
    private final ShinobiPlugin plugin;
    private BloodlineRegistry registry = BloodlineRegistry.defaults();

    public BloodlineService(ShinobiPlugin plugin) {
        this.plugin = plugin;
    }

    public BloodlineRegistry registry() {
        return registry;
    }

    /** Rebuilds the registry from defaults plus {@code bloodlines.<id>} config overrides. */
    public void reload() {
        BloodlineRegistry reg = BloodlineRegistry.defaults();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("bloodlines");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                BloodlineDef base = reg.get(id);
                ConfigurationSection c = root.getConfigurationSection(id);
                if (c == null) continue;
                if (base == null) {
                    plugin.getLogger().warning("bloodlines." + id + " is not a known bloodline and was ignored.");
                    continue;
                }
                BloodlineDef.Builder b = base.toBuilder();
                b.enabled(c.getBoolean("enabled", base.enabled));
                b.anyClan(c.getBoolean("any-clan", base.anyClan));
                if (c.contains("innate")) b.innate(c.getBoolean("innate"));
                if (c.contains("parent")) b.parent(c.getString("parent"));
                if (c.contains("rarity")) {
                    try {
                        b.rarity(BloodlineDef.Rarity.valueOf(c.getString("rarity", "RARE").toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().warning("bloodlines." + id + ".rarity must be COMMON, UNCOMMON, RARE, EPIC or LEGENDARY");
                    }
                }
                if (c.contains("eligible-clans")) {
                    Set<Clan> clans = new HashSet<>();
                    for (String s : c.getStringList("eligible-clans")) {
                        Clan cl = Clan.parse(s);
                        if (cl == null) plugin.getLogger().warning("bloodlines." + id + ": unknown clan '" + s + "'");
                        else clans.add(cl);
                    }
                    b.clans(clans);
                }
                reg.register(b.build());
            }
        }
        for (String problem : reg.validate()) plugin.getLogger().warning("bloodlines: " + problem);
        this.registry = reg;
    }

    /** Persisted grants plus anything implied by legacy (pre-bloodline) Mangekyo data. */
    public Set<String> unlockedFor(Profile pr) {
        Set<String> out = new HashSet<>(pr.bloodlines);
        if (pr.clan == Clan.UCHIHA) {
            if (pr.mangekyo != null || !pr.mangekyoPowers.isEmpty()) out.add("mangekyo_sharingan");
            if (pr.eternal) out.add("eternal_mangekyo");
        }
        return out;
    }

    public BloodlineRules.Result check(Profile pr, String bloodlineId) {
        if (bloodlineId != null && plugin.getConfig().getBoolean("admin.forced-grants-enabled", true)
                && pr.forcedBloodlines.contains(bloodlineId.toLowerCase(Locale.ROOT))) {
            return BloodlineRules.Result.OK; // administrator-forced access, stored explicitly on the profile
        }
        return BloodlineRules.check(registry, bloodlineId, pr.clan, pr.awakened, unlockedFor(pr));
    }

    /** Admin grant: only clan-eligible, enabled, non-innate bloodlines can be granted. Returns a human-readable outcome. */
    public String grant(Profile pr, String id) {
        BloodlineDef d = registry.get(id);
        if (d == null) return "Unknown bloodline '" + id + "'.";
        if (d.innate) return d.display + " is innate: eligible awakened " + (d.eligibleClans.isEmpty() ? "members" : d.eligibleClans.iterator().next().display) + " have it automatically.";
        Set<String> probe = new HashSet<>(unlockedFor(pr));
        probe.add(d.id);
        BloodlineRules.Result r = BloodlineRules.check(registry, d.id, pr.clan, pr.awakened, probe);
        if (r == BloodlineRules.Result.WRONG_CLAN || r == BloodlineRules.Result.DISABLED || r == BloodlineRules.Result.NOT_AWAKENED) {
            return "Cannot grant " + d.display + ": " + BloodlineRules.describe(r) + ".";
        }
        // MISSING_PARENT is allowed here on purpose: the grant is stored, but the check still blocks use until the chain is complete.
        pr.bloodlines.add(d.id);
        return "Granted " + d.display + (r == BloodlineRules.Result.MISSING_PARENT ? " (not usable until the earlier stage is reached)." : ".");
    }

    public boolean revoke(Profile pr, String id) {
        return pr.bloodlines.remove(id.toLowerCase(Locale.ROOT));
    }

    public List<String> describe(Profile pr) {
        List<String> out = new ArrayList<>();
        for (BloodlineDef d : registry.all()) {
            BloodlineRules.Result r = check(pr, d.id);
            out.add(d.id + " [" + d.kind + "/" + d.rarity + (d.enabled ? "" : ", disabled") + "]: " + r);
        }
        return out;
    }
}
