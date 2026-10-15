package dev.shinobi;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** The Eight Gates: opened in order, each stronger and costlier. */
public final class GateManager {
    private static final String[] NAMES = {
            "Gate of Opening", "Gate of Healing", "Gate of Life", "Gate of Pain",
            "Gate of Limit", "Gate of View", "Gate of Wonder", "Gate of Death"
    };

    private final ShinobiPlugin plugin;

    public GateManager(ShinobiPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p, Profile pr) {
        long now = System.currentTimeMillis();
        if (pr.gate >= 8) {
            Msg.bar(p, "<red>All eight gates are already open.");
            return;
        }
        if (now - pr.gateLastOpen < 3000L) {
            Msg.bar(p, "<red>Focus. You can't open gates that fast.");
            return;
        }
        double minHealth = plugin.getConfig().getDouble("gates.min-health-to-open", 12.0);
        if (pr.gate == 0 && p.getHealth() < minHealth) {
            Msg.bar(p, "<red>Your body is too weak to open the first gate.");
            return;
        }
        pr.gate++;
        pr.gateLastOpen = now;
        pr.gateTicks = 0;
        if (pr.gate == 8) pr.gate8Until = now + 30_000L;
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.8f, 1.4f);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.15);
        p.sendMessage(Msg.mm(Msg.PREFIX + "<green>" + NAMES[pr.gate - 1] + " opened.</green>"
                + (pr.gate == 8 ? " <dark_red>This is the last gate..." : "")));
        applyEffects(p, pr);
    }

    private void applyEffects(Player p, Profile pr) {
        int g = pr.gate;
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, Math.min(4, g / 2), true, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, Math.min(5, g - 1), true, false, false));
        if (g >= 3) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, Math.min(3, (g - 1) / 2), true, false, false));
        }
    }

    /** Called once per second. */
    public void tick(Player p, Profile pr) {
        if (pr.gate <= 0) return;
        applyEffects(p, pr);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), Math.max(2, pr.gate * 2), 0.4, 0.7, 0.4, 0.05);

        pr.gateTicks++;
        if (pr.gate == 8) {
            if (System.currentTimeMillis() > pr.gate8Until) {
                if (plugin.getConfig().getBoolean("gates.death-gate-lethal", false)) {
                    p.sendMessage(Msg.mm(Msg.PREFIX + "<dark_red>Your body gives out."));
                    pr.gate = 0;
                    p.setHealth(0.0);
                } else {
                    close(p, pr, "The Gate of Death closes. You barely survive.");
                    p.setHealth(1.0);
                }
            }
            return;
        }
        if (pr.gateTicks % 5 == 0) {
            double drain = 0.5 * pr.gate * (1.0 - Math.min(0.3, pr.weightedSeconds / 2000.0));
            if (p.getHealth() - drain <= 2.0) {
                close(p, pr, "Your body can't take any more.");
                return;
            }
            p.setHealth(p.getHealth() - drain);
        }
    }

    public void close(Player p, Profile pr, String reason) {
        if (pr.gate <= 0) {
            Msg.bar(p, "<gray>No gates are open.");
            return;
        }
        int level = pr.gate;
        pr.gate = 0;
        pr.gateTicks = 0;
        p.removePotionEffect(PotionEffectType.SPEED);
        p.removePotionEffect(PotionEffectType.STRENGTH);
        p.removePotionEffect(PotionEffectType.RESISTANCE);
        int dur = 20 * Math.min(60, 10 * level);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, dur, 1, true, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, dur, 1, true, false, true));
        p.sendMessage(Msg.mm(Msg.PREFIX + "<gray>" + (reason == null ? "The gates close. You are exhausted." : reason)));
    }
}
