package dev.shinobi;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Release effects for chargeable techniques. They only decide what the technique does; chakra, cooldowns and
 * lifecycle belong to {@link AbilityEngine} / {@link ChargeManager}. Every temporary task is registered with the
 * {@link EffectRegistry}. Nothing here edits blocks. Damage is dealt with {@code LivingEntity#damage(double, Entity)},
 * so it goes through normal damage events and protection plugins can veto it (not tested against any plugin).
 * Tunables live under {@code charging.jutsu.<id>} in config.yml.
 */
public final class ChargeJutsus {
    private ChargeJutsus() {}

    private static double d(ShinobiPlugin pl, String id, String key, double def) {
        return pl.getConfig().getDouble("charging.jutsu." + id + "." + key, def);
    }

    // ------------------------------------------------------------------ Chidori

    /** Charged forward dash. Hits at most one target, once. */
    public static boolean chidori(ShinobiPlugin plugin, Player p, Profile pr, Jutsu.ChargeResult c) {
        World w = p.getWorld();
        Vector dir = p.getLocation().getDirection().normalize();
        RayTraceResult wall = w.rayTraceBlocks(p.getEyeLocation(), dir, 1.2, FluidCollisionMode.NEVER, true);
        if (wall != null) {
            Msg.bar(p, "<red>No room to dash.");
            return false;
        }
        double speed = Math.min(d(plugin, "chidori", "max-dash-speed", 2.4), d(plugin, "chidori", "dash-speed", 1.6) * c.rangeScale());
        double damage = d(plugin, "chidori", "damage", 14.0) * c.damageScale();
        int life = (int) Math.max(4, Math.min(30, d(plugin, "chidori", "dash-ticks", 12)));

        EffectRegistry.Handle h = plugin.effects().begin(p.getUniqueId(), "chidori", () -> { });
        if (h == null) return false;
        Vector vel = dir.clone().multiply(speed);
        vel.setY(Math.max(0.15, dir.getY() * 0.6));
        p.setVelocity(vel);
        w.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.6f, 1.6f);

        final Set<UUID> hit = new HashSet<>();
        h.attach(new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                t++;
                if (t > life || !p.isOnline() || p.isDead()) {
                    h.end();
                    return;
                }
                Location body = p.getLocation().add(0, 1, 0);
                w.spawnParticle(Particle.ELECTRIC_SPARK, body, 14, 0.4, 0.5, 0.4, 0.15);
                Vector v = p.getVelocity();
                Vector fwd = v.lengthSquared() > 1.0e-4 ? v.clone().normalize() : dir; // never normalise a zero vector
                Location probe = body.clone().add(fwd.multiply(0.6));
                for (Entity en : w.getNearbyEntities(probe, 1.6, 1.6, 1.6)) {
                    if (!Fx.canHurt(plugin, p, en) || !hit.add(en.getUniqueId())) continue;
                    ((LivingEntity) en).damage(damage, p);
                    w.spawnParticle(Particle.ELECTRIC_SPARK, en.getLocation().add(0, 1, 0), 40, 0.5, 0.7, 0.5, 0.3);
                    w.playSound(en.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.8f);
                    h.end(); // one hit per cast
                    return;
                }
            }
        }.runTaskTimer(plugin, 1L, 1L));
        return true;
    }

    // ------------------------------------------------------------------ Rasengan

    /** Close-range impact. Needs a living target within reach, otherwise nothing fires (engine refunds per config). */
    public static boolean rasengan(ShinobiPlugin plugin, Player p, Profile pr, Jutsu.ChargeResult c) {
        double reach = Math.min(6.0, d(plugin, "rasengan", "reach", 3.5) * c.rangeScale());
        LivingEntity target = Fx.target(p, reach);
        if (target == null || !Fx.canHurt(plugin, p, target)) return false;
        double damage = d(plugin, "rasengan", "damage", 12.0) * c.damageScale();
        World w = p.getWorld();
        Location at = target.getLocation().add(0, 1, 0);
        target.damage(damage, p);
        Vector kb = p.getLocation().getDirection().setY(0.3).normalize().multiply(Math.min(2.2, 1.1 * c.damageScale()));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (target.isValid()) target.setVelocity(kb);
        }, 1L);
        w.spawnParticle(Particle.EXPLOSION, at, 1);
        w.spawnParticle(Particle.CLOUD, at, 25, 0.5, 0.5, 0.5, 0.15);
        w.spawnParticle(Particle.END_ROD, at, 18, 0.5, 0.5, 0.5, 0.2);
        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.5f);
        return true;
    }

    // ------------------------------------------------------------------ Rasenshuriken

    /** Bounded-lifetime projectile with a capped area burst on impact. Never changes blocks. */
    public static boolean rasenshuriken(ShinobiPlugin plugin, Player p, Profile pr, Jutsu.ChargeResult c) {
        EffectRegistry.Handle h = plugin.effects().begin(p.getUniqueId(), "rasenshuriken", () -> { });
        if (h == null) return false;
        World w = p.getWorld();
        Vector dir = p.getEyeLocation().getDirection().normalize();
        double speed = d(plugin, "rasenshuriken", "speed", 1.1);
        int maxLife = (int) Math.min(60, Math.max(5, d(plugin, "rasenshuriken", "projectile-ticks", 30) * c.rangeScale()));
        double radius = Math.min(d(plugin, "rasenshuriken", "max-radius", 5.0), d(plugin, "rasenshuriken", "radius", 3.5) * (0.85 + 0.3 * c.progress()));
        double damage = d(plugin, "rasenshuriken", "damage", 18.0) * c.damageScale();
        final Location cur = p.getEyeLocation().add(dir.clone().multiply(1.0));
        w.playSound(cur, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.6f);

        h.attach(new BukkitRunnable() {
            int life = 0;

            @Override
            public void run() {
                life++;
                if (!p.isOnline()) {
                    h.end();
                    return;
                }
                RayTraceResult br = w.rayTraceBlocks(cur, dir, speed, FluidCollisionMode.NEVER, true);
                boolean blocked = br != null;
                if (blocked) cur.setX(br.getHitPosition().getX());
                if (blocked) {
                    cur.setY(br.getHitPosition().getY());
                    cur.setZ(br.getHitPosition().getZ());
                } else {
                    cur.add(dir.clone().multiply(speed));
                }
                boolean entityHit = !w.getNearbyEntities(cur, 1.2, 1.2, 1.2, e -> Fx.canHurt(plugin, p, e)).isEmpty();
                if (blocked || entityHit || life >= maxLife) {
                    burst();
                    h.end();
                    return;
                }
                double a = life * 0.9;
                w.spawnParticle(Particle.CLOUD, cur, 6, 0.25, 0.25, 0.25, 0.02);
                w.spawnParticle(Particle.END_ROD, cur.clone().add(Math.cos(a) * 0.6, Math.sin(a) * 0.6, 0), 3, 0.05, 0.05, 0.05, 0.0);
                w.spawnParticle(Particle.END_ROD, cur.clone().add(-Math.cos(a) * 0.6, -Math.sin(a) * 0.6, 0), 3, 0.05, 0.05, 0.05, 0.0);
            }

            private void burst() {
                Set<UUID> done = new HashSet<>();
                for (Entity en : w.getNearbyEntities(cur, radius, radius, radius)) {
                    if (!Fx.canHurt(plugin, p, en) || !done.add(en.getUniqueId())) continue;
                    LivingEntity le = (LivingEntity) en;
                    le.damage(damage, p);
                    Vector out = le.getLocation().toVector().subtract(cur.toVector()).setY(0.4);
                    if (out.lengthSquared() > 1.0e-6) le.setVelocity(out.normalize().multiply(1.0));
                }
                w.spawnParticle(Particle.EXPLOSION, cur, 2, 0.3, 0.3, 0.3, 0.0);
                w.spawnParticle(Particle.CLOUD, cur, 60, radius * 0.4, radius * 0.3, radius * 0.4, 0.25);
                w.spawnParticle(Particle.END_ROD, cur, 40, radius * 0.4, radius * 0.3, radius * 0.4, 0.3);
                w.playSound(cur, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.3f);
            }
        }.runTaskTimer(plugin, 0L, 1L));
        return true;
    }

    // ------------------------------------------------------------------ Fire Release: Great Fireball

    public static boolean fireball(ShinobiPlugin plugin, Player p, Profile pr, Jutsu.ChargeResult c) {
        return fireball(plugin, p, c.damageScale(), c.rangeScale());
    }

    /** Also used by the instant (non-charged) fallback with scales of 1.0. */
    public static boolean fireball(ShinobiPlugin plugin, Player p, double damageScale, double rangeScale) {
        EffectRegistry.Handle h = plugin.effects().begin(p.getUniqueId(), "great_fireball", () -> { });
        if (h == null) return false;
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location cur = eye.clone();
        double damage = d(plugin, "great_fireball", "damage", 8.0) * damageScale;
        int steps = (int) Math.min(40, Math.max(6, d(plugin, "great_fireball", "range-steps", 22) * rangeScale));
        Set<UUID> hit = new HashSet<>();
        w.playSound(eye, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.7f);
        h.attach(new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                step++;
                if (step > steps || !p.isOnline()) {
                    h.end();
                    return;
                }
                cur.add(dir);
                if (cur.getBlock().getType().isSolid()) {
                    w.spawnParticle(Particle.POOF, cur, 20, 0.4, 0.4, 0.4, 0.05);
                    h.end();
                    return;
                }
                w.spawnParticle(Particle.FLAME, cur, 30, 0.5, 0.5, 0.5, 0.02);
                w.spawnParticle(Particle.SMOKE, cur, 6, 0.4, 0.4, 0.4, 0.01);
                for (Entity en : w.getNearbyEntities(cur, 1.8, 1.8, 1.8)) {
                    if (Fx.canHurt(plugin, p, en) && hit.add(en.getUniqueId())) {
                        LivingEntity le = (LivingEntity) en;
                        le.damage(damage, p);
                        le.setFireTicks(100); // sets the entity alight only; no block fire
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L));
        return true;
    }
}
