package dev.shinobi;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Shadow Clones: short-lived fighters that vanish the moment they are hurt. */
public final class CloneManager implements Listener {
    private final ShinobiPlugin plugin;
    private final NamespacedKey cloneKey;
    private final Random random = new Random();

    public CloneManager(ShinobiPlugin plugin) {
        this.plugin = plugin;
        this.cloneKey = new NamespacedKey(plugin, "shadow_clone");
    }

    private boolean isClone(Entity e) {
        return e instanceof Husk && e.getPersistentDataContainer().has(cloneKey, PersistentDataType.STRING);
    }

    public boolean spawn(Player p, Profile pr) {
        int count = Math.min(4, 2 + pr.masteryOf("shadow_clone") / 15);
        World w = p.getWorld();
        List<Husk> clones = new ArrayList<>();

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(p);
        head.setItemMeta(sm);

        for (int i = 0; i < count; i++) {
            Location loc = p.getLocation().add((random.nextDouble() - 0.5) * 4, 0, (random.nextDouble() - 0.5) * 4);
            if (!Fx.isSafe(loc)) loc = p.getLocation();
            Husk h = w.spawn(loc, Husk.class, c -> {
                c.setBaby(false);
                c.setSilent(true);
                c.setPersistent(false);
                c.setRemoveWhenFarAway(true);
                c.setCanPickupItems(false);
                c.customName(Msg.mm("<gray>" + p.getName() + " <dark_gray>(clone)"));
                c.setCustomNameVisible(true);
                c.getEquipment().setHelmet(head.clone());
                c.getEquipment().setHelmetDropChance(0f);
                c.getPersistentDataContainer().set(cloneKey, PersistentDataType.STRING, p.getUniqueId().toString());
            });
            clones.add(h);
            w.spawnParticle(Particle.POOF, loc.clone().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.05);
        }
        w.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1f, 1.2f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks += 10;
                boolean over = ticks > 400 || !p.isOnline();
                for (Husk h : clones) {
                    if (!h.isValid()) continue;
                    if (over) {
                        vanish(h);
                        continue;
                    }
                    Monster target = null;
                    double best = Double.MAX_VALUE;
                    for (Entity en : h.getNearbyEntities(12, 6, 12)) {
                        if (en instanceof Monster m && !isClone(m)) {
                            double d = m.getLocation().distanceSquared(h.getLocation());
                            if (d < best) {
                                best = d;
                                target = m;
                            }
                        }
                    }
                    if (target != null) {
                        h.setTarget(target);
                    } else {
                        h.setTarget(null);
                        double dist = h.getWorld().equals(p.getWorld()) ? h.getLocation().distance(p.getLocation()) : 999.0;
                        if (dist > 25.0) h.teleport(p.getLocation());
                        else if (dist > 4.0) h.getPathfinder().moveTo(p.getLocation(), 1.2);
                    }
                }
                if (over) cancel();
            }
        }.runTaskTimer(plugin, 10L, 10L);
        return true;
    }

    private void vanish(Entity h) {
        h.getWorld().spawnParticle(Particle.POOF, h.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.05);
        h.remove();
    }

    public void removeAll() {
        for (World w : plugin.getServer().getWorlds()) {
            for (Husk h : w.getEntitiesByClass(Husk.class)) if (isClone(h)) h.remove();
        }
    }

    /** Clones vanish when anything hurts them. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!isClone(e.getEntity())) return;
        e.setCancelled(true);
        vanish(e.getEntity());
    }

    /** Clones only ever fight monsters, never players or other clones. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onTarget(EntityTargetEvent e) {
        if (!isClone(e.getEntity())) return;
        if (!(e.getTarget() instanceof Monster m) || isClone(m)) e.setCancelled(true);
    }
}
