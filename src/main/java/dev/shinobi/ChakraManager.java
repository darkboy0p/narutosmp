package dev.shinobi;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public final class ChakraManager {
    private final ShinobiPlugin plugin;

    public ChakraManager(ShinobiPlugin plugin) {
        this.plugin = plugin;
    }

    public double effectiveMax(Profile pr) {
        double max = pr.maxChakra;
        if (pr.clan == Clan.UZUMAKI && pr.awakened) max *= 1.5;
        return max;
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : plugin.getServer().getOnlinePlayers()) secondTick(p);
            }
        }.runTaskTimer(plugin, 20L, 20L);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.getConfig().getBoolean("hud.enabled", true)) return;
                for (Player p : plugin.getServer().getOnlinePlayers()) hudTick(p);
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    private void secondTick(Player p) {
        Profile pr = plugin.profiles().get(p);
        double max = effectiveMax(pr);
        boolean sealing = pr.session != null;

        Location l = p.getLocation();
        boolean still = l.getBlockX() == pr.lastX && l.getBlockY() == pr.lastY && l.getBlockZ() == pr.lastZ;
        pr.lastX = l.getBlockX();
        pr.lastY = l.getBlockY();
        pr.lastZ = l.getBlockZ();
        boolean inFlow = Fx.inFlowingWater(p);
        boolean meditating = p.isSneaking() && still && !sealing && (p.isOnGround() || inFlow);

        double regen = plugin.getConfig().getDouble("chakra.base-regen", 2.0);
        if (pr.clan == Clan.UZUMAKI && pr.awakened) regen *= 1.5;
        if (meditating) {
            regen *= plugin.getConfig().getDouble("chakra.meditation-multiplier", 3.0);
            if (inFlow) regen *= 1.5;
            double cap = plugin.getConfig().getDouble("chakra.max-cap", 400.0);
            pr.maxChakra = Math.min(cap, pr.maxChakra + plugin.getConfig().getDouble("chakra.meditation-growth", 0.05));
            p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 0.3, 0), 3, 0.4, 0.1, 0.4, 0.01);
            plugin.missions().progress(p, pr, MissionType.MEDITATE, "ANY", 1);
            if (inFlow) plugin.missions().progress(p, pr, MissionType.WATERFALL, "ANY", 1);
        }

        double upkeep = pr.dojutsuActive ? plugin.getConfig().getDouble("chakra.dojutsu-upkeep", 2.0) : 0.0;
        pr.chakra = Math.max(0.0, Math.min(max, pr.chakra + regen - upkeep));
        if (pr.dojutsuActive && pr.chakra <= 0.0) {
            plugin.dojutsu().deactivate(p, pr, "Out of chakra.");
        }

        // Mangekyou strain: darkness at 50+, blindness at 100, slow recovery (faster while meditating).
        if (pr.strain > 0) {
            pr.strain = Math.max(0.0, pr.strain - (meditating ? 3.0 : 1.0));
            if (pr.strain >= 100.0) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 200, 0, true, false, false));
                p.sendMessage(Msg.mm(Msg.PREFIX + "<dark_red>Your eyes give out from the strain!"));
                pr.strain = 70.0;
            } else if (pr.strain >= 50.0) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0, true, false, false));
            }
        }

        plugin.dojutsu().tick(p, pr);
        plugin.gates().tick(p, pr);
        plugin.susanoo().secondTick(p, pr);
        plugin.training().secondTick(p, pr);
        plugin.missions().tickDelivery(p, pr);
    }

    private void hudTick(Player p) {
        Profile pr = plugin.profiles().get(p);
        long now = System.currentTimeMillis();

        if (pr.session != null) {
            if (now - pr.session.last > plugin.getConfig().getLong("seal-mode.idle-exit-ms", 15000L)) {
                plugin.seals().exit(p, pr);
            } else {
                plugin.seals().showSequence(p, pr);
                return;
            }
        }

        double max = effectiveMax(pr);
        int total = 20;
        int filled = (int) Math.round(total * Math.min(1.0, pr.chakra / Math.max(1.0, max)));
        StringBuilder sb = new StringBuilder();
        boolean pack = plugin.hud().on();
        if (pack && pr.natureRevealed && pr.nature != null) sb.append(plugin.hud().natureGlyph(pr.nature)).append(' ');
        sb.append("<aqua>Chakra ");
        if (plugin.hud().on()) {
            sb.append(plugin.hud().bar(filled, total));
        } else {
            sb.append("<white>").append("\u2588".repeat(Math.max(0, filled)));
            sb.append("<dark_gray>").append("\u2588".repeat(Math.max(0, total - filled)));
        }
        sb.append(" <gray>").append((int) pr.chakra).append("/").append((int) max);
        if (pr.dojutsuActive) {
            String name = pr.clan != null && pr.clan.dojutsuName() != null ? pr.clan.dojutsuName() : "Dojutsu";
            sb.append("  ");
            if (pack) sb.append(plugin.hud().eyeGlyph(pr)).append(' ');
            sb.append("<red>").append(name);
        }
        if (pr.gate > 0) {
            sb.append("  ");
            if (pack) sb.append(plugin.hud().gateGlyph(pr.gate)).append(' ');
            sb.append("<green>Gate ").append(pr.gate);
        }
        int sus = plugin.susanoo().stage(p);
        if (sus > 0) sb.append("  <dark_purple>Susanoo ").append(sus);
        if (pr.strain >= 10) sb.append("  <dark_purple>Strain ").append((int) pr.strain).append("%");
        if (pr.wallWalk) sb.append("  <green>Climb");
        if (pr.waterWalk) sb.append("  <aqua>Water");
        if (now < pr.sealedUntil) sb.append("  <red>SEALED");
        Msg.bar(p, sb.toString());
    }
}
