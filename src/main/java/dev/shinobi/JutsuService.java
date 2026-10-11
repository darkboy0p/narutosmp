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
        if (pr.clan == Clan.HATAKE && pr.awakened && j.id.equals("white_light_sabre")) cost *= 1.0 - (0.2 + 0.1 * (clanLevel(pr) - 1));
        if (pr.clan == Clan.NARA && pr.awakened && j.id.equals("shadow_bind")) cost *= 1.0 - 0.1 * clanLevel(pr);
        return cost;
    }

    public boolean cast(Player p, Profile pr, Jutsu j) {
        long now = System.currentTimeMillis();
        if (now < pr.sealedUntil) {
            Msg.bar(p, "<red>Your chakra points are sealed!");
            return false;
        }
        boolean copied = false;
        if (j.needsKnown && !unlocked(pr, j)) {
            Long exp = pr.copied.get(j.id);
            if (exp != null && exp > now) {
                copied = true;
            } else {
                Msg.bar(p, "<red>You haven't learned <white>" + j.name + "<red>.");
                return false;
            }
        }
        if (j.nature != null && pr.nature != j.nature && !easy("ignore-nature")) {
            Msg.bar(p, "<red>Your chakra nature does not match <white>" + j.name + "<red>.");
            return false;
        }
        if (j.clan != null && (pr.clan != j.clan || !pr.awakened)) {
            Msg.bar(p, "<red>Only an awakened " + j.clan.display + " can use that.");
            return false;
        }
        if (j.needsDojutsu && !pr.dojutsuActive) {
            Msg.bar(p, "<red>Activate your dojutsu first.");
            return false;
        }
        Long cd = pr.cooldowns.get(j.id);
        if (cd != null && cd > now) {
            Msg.bar(p, "<red>" + j.name + " on cooldown: " + String.format("%.1f", (cd - now) / 1000.0) + "s");
            return false;
        }

        double cost = costFor(pr, j) * (copied ? 1.5 : 1.0);
        long cooldown = j.cooldownMs;
        if (pr.eternal && j.id.equals("mangekyo_ability")) {
            cost *= 0.85;
            cooldown = (long) (cooldown * 0.7);
        }
        if (pr.chakra < cost) {
            Msg.bar(p, "<red>Not enough chakra (" + (int) Math.ceil(cost) + " needed).");
            return false;
        }
        if (!j.effect.cast(plugin, p, pr)) return false;

        pr.chakra -= cost;
        pr.cooldowns.put(j.id, now + cooldown);
        if (!copied) pr.mastery.merge(j.id, 1, Integer::sum);
        if (j.cost > 0) {
            Msg.bar(p, "<gold>" + j.name + "!");
            plugin.missions().progress(p, pr, MissionType.CAST, j.id, 1);
        }
        if (j.needsKnown) shareWithSharingan(p, j, now);
        return true;
    }

    /** Awakened Uchiha with an active Sharingan, watching the caster, copy what they see. */
    private void shareWithSharingan(Player caster, Jutsu j, long now) {
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
