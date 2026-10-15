package dev.shinobi;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class JutsuService {
    private final ShinobiPlugin plugin;

    public JutsuService(ShinobiPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean easy(String key) {
        return plugin.getConfig().getBoolean("easy-mode." + key, true);
    }

    /** True if the player may cast this technique without a copy (scroll learned, or easy-mode unlock-all). */
    public boolean unlocked(Profile pr, Jutsu j) {
        if (!j.needsKnown) return true;
        return easy("unlock-all-jutsu") || pr.known.contains(j.id);
    }

    /** True if the technique should appear in the player's lists / book. */
    public boolean usable(Profile pr, Jutsu j) {
        if (j.clan != null) return pr.clan == j.clan && pr.awakened;
        return unlocked(pr, j);
    }

    /** Clan level 1-3 for every clan: grows with training (25 / 75 total casts) or is set by an admin. */
    public int clanLevel(Profile pr) {
        return plugin.hud().sharinganStage(pr);
    }

    public double costFor(Profile pr, Jutsu j) {
        double cost = j.cost * (1.0 - Math.min(0.30, pr.masteryOf(j.id) * 0.003));
        if (pr.clan == Clan.UCHIHA && pr.awakened && j.nature == Nature.FIRE) cost *= 0.8;
        if (j.id.equals("sharingan_dodge")) cost *= plugin.dodges().tierFor(pr).costMultiplier();
        if (pr.clan == Clan.SENJU && pr.awakened && j.id.equals("white_light_sabre")) cost *= 1.0 - (0.2 + 0.1 * (clanLevel(pr) - 1));
        if (pr.clan == Clan.NARA && pr.awakened && j.id.equals("shadow_bind")) cost *= 1.0 - 0.1 * clanLevel(pr);
        return cost;
    }

    /** Validation, charging and cooldowns live in {@link AbilityEngine}. */
    public boolean cast(Player p, Profile pr, Jutsu j) {
        return plugin.abilities().cast(p, pr, j);
    }

    /** Awakened Uchiha with an active Sharingan, watching the caster, copy what they see. */
    void shareWithSharingan(Player caster, Jutsu j, long now) {
        double minutes = plugin.getConfig().getDouble("sharingan.copy-minutes", 10.0);
        double range = plugin.getConfig().getDouble("sharingan.copy-range", 25.0);
        long ttl = (long) (minutes * 60_000L);
        for (Player o : caster.getWorld().getPlayers()) {
            if (o.equals(caster)) continue;
            Profile op = plugin.profiles().get(o);
            if (op.clan != Clan.UCHIHA || !op.awakened || !op.dojutsuActive) continue;
            if (unlocked(op, j)) continue;
            if (j.nature != null && op.nature != j.nature) continue;
            if (o.getLocation().distance(caster.getLocation()) > range) continue;
            Vector to = caster.getEyeLocation().toVector().subtract(o.getEyeLocation().toVector());
            if (to.lengthSquared() < 0.01) continue;
            if (o.getEyeLocation().getDirection().angle(to.normalize()) > 1.2f) continue;

            op.copied.put(j.id, now + ttl);
            while (op.copied.size() > 3) {
                String first = op.copied.keySet().iterator().next();
                op.copied.remove(first);
            }
            o.playSound(o.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.5f);
            Msg.bar(o, "<red>Sharingan copied <white>" + j.name + " <gray>(" + (int) minutes + " min, costs more)");
        }
    }
}
