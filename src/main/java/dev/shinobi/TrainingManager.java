package dev.shinobi;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** Chakra-control training: Tree Climbing, Water Walking and weighted training. */
public final class TrainingManager {
    private final ShinobiPlugin plugin;

    public TrainingManager(ShinobiPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    Profile pr = plugin.profiles().get(p);
                    if (pr.wallWalk) wall(p, pr);
                    if (pr.waterWalk) water(p, pr);
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    public boolean toggleWall(Player p, Profile pr) {
        if (pr.wallWalk) {
            pr.wallWalk = false;
            Msg.bar(p, "<gray>Tree Climbing released.");
            return true;
        }
        if (pr.chakra < 10) {
            Msg.bar(p, "<red>Not enough chakra.");
            return false;
        }
        pr.wallWalk = true;
        Msg.bar(p, "<green>Tree Climbing: touch a wall. <gray>Sneak = climb, release = cling. Costs 3 chakra/s.");
        return true;
    }

    public boolean toggleWater(Player p, Profile pr) {
        if (pr.waterWalk) {
            pr.waterWalk = false;
            Msg.bar(p, "<gray>Water Walking released.");
            return true;
        }
        if (pr.chakra < 10) {
            Msg.bar(p, "<red>Not enough chakra.");
            return false;
        }
        pr.waterWalk = true;
        Msg.bar(p, "<aqua>Water Walking: step onto water. <gray>Costs 2 chakra/s.");
        return true;
    }

    public void toggleWeights(Player p, Profile pr) {
        pr.weights = !pr.weights;
        Msg.send(p, pr.weights ? "<gold>Training weights on. Sprint to train." : "<gray>Training weights removed.");
    }

    public void stopAll(Profile pr) {
        pr.wallWalk = false;
        pr.waterWalk = false;
        pr.weights = false;
    }

    // ------------------------------------------------------------------ 2-tick physics

    private static boolean adjacentWall(Player p) {
        Location l = p.getLocation();
        double[][] offs = {{0.45, 0}, {-0.45, 0}, {0, 0.45}, {0, -0.45}};
        for (double h : new double[]{0.3, 1.3}) {
            for (double[] o : offs) {
                if (l.clone().add(o[0], h, o[1]).getBlock().getType().isSolid()) return true;
            }
        }
        return false;
    }

    private void wall(Player p, Profile pr) {
        pr.climbingNow = false;
        if (pr.chakra < 1.0) {
            pr.wallWalk = false;
            Msg.bar(p, "<red>Out of chakra. You lose your grip.");
            return;
        }
        if (!adjacentWall(p)) return;
        if (p.isSneaking()) {
            p.setVelocity(new Vector(0, 0.22, 0));
            pr.climbingNow = true;
        } else if (!p.isOnGround()) {
            p.setVelocity(new Vector(0, 0, 0));
            pr.climbingNow = true;
        }
        if (pr.climbingNow) {
            p.setFallDistance(0f);
            p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 0.2, 0), 1, 0.2, 0.1, 0.2, 0.0);
        }
    }

    private void water(Player p, Profile pr) {
        pr.waterNow = false;
        if (pr.chakra < 1.0) {
            pr.waterWalk = false;
            Msg.bar(p, "<red>Out of chakra. You sink.");
            return;
        }
        Location l = p.getLocation();
        World w = l.getWorld();
        if (l.getBlock().getType() == Material.WATER) {
            p.setVelocity(p.getVelocity().setY(0.28));
        }
        int y = l.getBlockY() - 1;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block b = w.getBlockAt(l.getBlockX() + dx, y, l.getBlockZ() + dz);
                if (b.getType() == Material.WATER
                        && b.getRelative(BlockFace.UP).getType().isAir()
                        && b.getBlockData() instanceof Levelled lv && lv.getLevel() == 0) {
                    b.setType(Material.FROSTED_ICE, false);
                    pr.waterNow = true;
                } else if (b.getType() == Material.FROSTED_ICE && dx == 0 && dz == 0) {
                    pr.waterNow = true;
                }
            }
        }
    }

    // ------------------------------------------------------------------ once per second

    public void secondTick(Player p, Profile pr) {
        if (pr.wallWalk) {
            if (pr.climbingNow) {
                pr.chakra = Math.max(0.0, pr.chakra - 3.0);
                plugin.missions().progress(p, pr, MissionType.TREE_CLIMB, "ANY", 1);
            }
        }
        if (pr.waterWalk) {
            if (pr.waterNow) {
                pr.chakra = Math.max(0.0, pr.chakra - 2.0);
                plugin.missions().progress(p, pr, MissionType.WATER_WALK, "ANY", 1);
            }
        }
        if (pr.weights) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0, true, false, false));
            if (p.isSprinting()) {
                pr.weightedSeconds += 1.0;
                plugin.missions().progress(p, pr, MissionType.WEIGHTS, "ANY", 1);
            }
        }
    }
}
