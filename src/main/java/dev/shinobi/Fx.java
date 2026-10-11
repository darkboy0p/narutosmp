package dev.shinobi;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Small shared helpers for jutsu effects. */
public final class Fx {
    private Fx() {}

    public static boolean canHurt(ShinobiPlugin plugin, Player caster, Entity target) {
        if (!(target instanceof LivingEntity)) return false;
        if (target.equals(caster)) return false;
        if (target instanceof ArmorStand) return false;
        if (target instanceof Player && !plugin.getConfig().getBoolean("pvp", true)) return false;
        return true;
    }

    /** First living entity in the caster's line of sight, blocked by solid blocks. */
    public static LivingEntity target(Player p, double range) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        World w = p.getWorld();
        RayTraceResult er = w.rayTraceEntities(eye, dir, range, 0.6,
                e -> e instanceof LivingEntity && !e.equals(p) && !(e instanceof ArmorStand));
        if (er == null || er.getHitEntity() == null) return null;
        RayTraceResult br = w.rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        if (br != null && br.getHitPosition().distance(eye.toVector()) < er.getHitPosition().distance(eye.toVector())) {
            return null;
        }
        return (LivingEntity) er.getHitEntity();
    }

    public static boolean isSafe(Location l) {
        return l.getBlock().isPassable()
                && l.clone().add(0, 1, 0).getBlock().isPassable()
                && l.clone().add(0, -1, 0).getBlock().getType().isSolid();
    }

    /** Draws a line of particles between two points. */
    public static void line(World w, org.bukkit.Particle particle, Location from, Location to, int points) {
        Vector step = to.toVector().subtract(from.toVector()).multiply(1.0 / points);
        Location cur = from.clone();
        for (int i = 0; i < points; i++) {
            w.spawnParticle(particle, cur, 1, 0, 0, 0, 0);
            cur.add(step);
        }
    }

    public static boolean isClear(Location l) {
        return l.getBlock().isPassable() && l.clone().add(0, 1, 0).getBlock().isPassable();
    }

    public static boolean inFlowingWater(Player p) {
        Block feet = p.getLocation().getBlock();
        if (feet.getType() == Material.WATER && feet.getBlockData() instanceof Levelled lv && lv.getLevel() > 0) return true;
        Block head = p.getEyeLocation().getBlock();
        return head.getType() == Material.WATER && head.getBlockData() instanceof Levelled lv2 && lv2.getLevel() > 0;
    }

    /** A travelling ray that damages each entity it passes through once. */
    public static void beam(ShinobiPlugin plugin, Player p, Vector dir, int steps, double radius, double damage,
                            Particle particle, int count, int fireTicks, double knock) {
        World w = p.getWorld();
        Vector d = dir.clone().normalize();
        Location cur = p.getEyeLocation();
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                step++;
                if (step > steps || !p.isOnline()) {
                    cancel();
                    return;
                }
                cur.add(d);
                if (cur.getBlock().getType().isSolid()) {
                    cancel();
                    return;
                }
                w.spawnParticle(particle, cur, count, 0.25, 0.25, 0.25, 0.02);
                for (Entity en : w.getNearbyEntities(cur, radius, radius, radius)) {
                    if (canHurt(plugin, p, en) && hit.add(en.getUniqueId())) {
                        LivingEntity le = (LivingEntity) en;
                        le.damage(damage, p);
                        if (fireTicks > 0) le.setFireTicks(fireTicks);
                        if (knock > 0) le.setVelocity(d.clone().multiply(knock).setY(0.3));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Places a temporary wall in front of the caster. Returns how many blocks were placed. */
    public static int tempWall(ShinobiPlugin plugin, Player p, Material mat, int halfWidth, int height, long ticks) {
        Vector dir = p.getEyeLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 0.01) dir = new Vector(0, 0, 1);
        dir.normalize();
        Vector right = new Vector(-dir.getZ(), 0, dir.getX());
        Location center = p.getLocation().add(dir.clone().multiply(3));
        World w = p.getWorld();
        int placed = 0;
        for (int x = -halfWidth; x <= halfWidth; x++) {
            for (int y = 0; y < height; y++) {
                Block b = center.clone().add(right.clone().multiply(x)).add(0, y, 0).getBlock();
                if (!w.getNearbyEntities(b.getLocation().add(0.5, 0.5, 0.5), 0.5, 0.5, 0.5).isEmpty()) continue;
                if (plugin.tempBlocks().place(b, mat, ticks)) placed++;
            }
        }
        return placed;
    }
}
