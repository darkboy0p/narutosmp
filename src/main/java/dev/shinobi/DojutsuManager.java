package dev.shinobi;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class DojutsuManager {
    private final ShinobiPlugin plugin;

    public DojutsuManager(ShinobiPlugin plugin) {
        this.plugin = plugin;
    }

    public void toggle(Player p, Profile pr) {
        if (pr.clan == null || !pr.awakened || !pr.clan.hasDojutsu()) {
            Msg.bar(p, "<red>You have no dojutsu to activate.");
            return;
        }
        if (pr.dojutsuActive) {
            deactivate(p, pr, null);
            return;
        }
        BloodlineRules.Result br = plugin.bloodlines().check(pr, pr.clan == Clan.UCHIHA ? "sharingan" : "byakugan");
        if (br != BloodlineRules.Result.OK) {
            Msg.bar(p, "<red>" + BloodlineRules.describe(br) + ".");
            return;
        }
        if (pr.chakra < 10) {
            Msg.bar(p, "<red>Not enough chakra.");
            return;
        }
        pr.dojutsuActive = true;
        String name = pr.clan.dojutsuName();
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.6f);
        Msg.bar(p, pr.clan.color + name + " activated.");
        plugin.headgear().refresh(p);
        tick(p, pr);
    }

    public void deactivate(Player p, Profile pr, String reason) {
        if (!pr.dojutsuActive) return;
        pr.dojutsuActive = false;
        plugin.susanoo().end(p);
        p.removePotionEffect(PotionEffectType.NIGHT_VISION);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.6f);
        Msg.bar(p, "<gray>Dojutsu deactivated." + (reason == null ? "" : " " + reason));
        plugin.headgear().refresh(p);
    }

    /** Called once per second while the player is online. */
    public void tick(Player p, Profile pr) {
        if (!pr.dojutsuActive || pr.clan == null) return;
        plugin.headgear().refresh(p); // keeps the face in sync with stage changes
        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 400, 0, true, false, false));

        boolean byakugan = pr.clan == Clan.HYUGA;
        double radius = byakugan ? 24.0 + 8.0 * (plugin.casting().clanLevel(pr) - 1) : 16.0;
        Particle particle = byakugan ? Particle.END_ROD : Particle.FLAME;
        int shown = 0;
        for (Entity en : p.getNearbyEntities(radius, radius, radius)) {
            if (!(en instanceof LivingEntity le) || en instanceof org.bukkit.entity.ArmorStand) continue;
            if (shown++ >= 20) break;
            Location top = le.getEyeLocation().add(0, 0.6, 0);
            // Player#spawnParticle: only the dojutsu user sees the highlight.
            p.spawnParticle(particle, top, 3, 0.15, 0.1, 0.15, 0.0);
        }
    }
}
