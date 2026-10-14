package dev.shinobi;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sharingan dodge: a validated short dash with afterimage particles and a brief, capped damage-avoidance window.
 * Chakra, cooldown and eligibility are handled by the AbilityEngine (the dodge is the {@code sharingan_dodge} jutsu);
 * this class provides the activation (double-tap sneak), the dash and the avoidance window. Rules live in {@link DodgeRules}.
 * Server-side only: Paper cannot read movement keys, so direction comes from recent movement, else backwards.
 */
public final class DodgeManager implements Listener {
    private final ShinobiPlugin plugin;
    private final Map<UUID, Long> lastSneak = new HashMap<>();
    private final Map<UUID, double[]> lastMove = new HashMap<>(); // {dx, dz, timestampMs}
    private final Map<UUID, Long> iframeUntil = new HashMap<>();
    private long dodgesDone;
    private long hitsAvoided;

    public DodgeManager(ShinobiPlugin plugin) {
        this.plugin = plugin;
    }

    public String stats() {
        return "dodges=" + dodgesDone + " hitsAvoided=" + hitsAvoided + " activeWindows=" + iframeUntil.size();
    }

    // ------------------------------------------------------------------ tiers

    public DodgeRules.Tier tierFor(Profile pr) {
        boolean eternal = plugin.bloodlines().check(pr, "eternal_mangekyo") == BloodlineRules.Result.OK;
        boolean mangekyo = eternal || plugin.bloodlines().check(pr, "mangekyo_sharingan") == BloodlineRules.Result.OK;
        String key = DodgeRules.stageKey(plugin.hud().sharinganStage(pr), mangekyo, eternal);
        DodgeRules.Tier def = DodgeRules.defaultTier(key);
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("sharingan.dodge.tiers." + key);
        if (c == null) return def;
        return DodgeRules.tier(key, c.getInt("cooldown-ticks", def.cooldownTicks()), c.getInt("iframe-ticks", def.iframeTicks()),
                c.getDouble("distance", def.distance()), c.getDouble("cost-multiplier", def.costMultiplier()));
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("sharingan.dodge.enabled", true);
    }

    // ------------------------------------------------------------------ the dodge

    /** Called by the engine as the effect of {@code sharingan_dodge}. Returns false (nothing charged) if the dodge cannot happen. */
    public boolean dodge(Player p, Profile pr) {
        if (!enabled()) {
            Msg.bar(p, "<red>The Sharingan dodge is disabled.");
            return false;
        }
        DodgeRules.Tier tier = tierFor(pr);
        long now = System.currentTimeMillis();
        long sampleMs = Math.max(100, plugin.getConfig().getLong("sharingan.dodge.movement-sample-ms", 350L));
        double[] mv = lastMove.get(p.getUniqueId());
        double dx = 0, dz = 0;
        if (mv != null && now - (long) mv[2] <= sampleMs) {
            dx = mv[0];
            dz = mv[1];
        }
        double[] dir = DodgeRules.direction(dx, dz, p.getLocation().getYaw());

        Location dest = findDestination(p, dir[0], dir[1], tier.distance());
        if (dest == null) {
            Msg.bar(p, "<red>No safe room to dodge that way.");
            return false;
        }
        Location start = p.getLocation().clone();
        if (!p.teleport(dest)) { // another plugin (e.g. region protection) may veto the move
            Msg.bar(p, "<red>You cannot dodge here.");
            return false;
        }
        dodgesDone++;
        openWindow(p, tier);
        afterimage(start, dest, tier);
        p.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.8f);
        p.getWorld().playSound(dest, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6f, 0.6f + 0.2f * tier.iframeTicks());
        return true;
    }

    private void openWindow(Player p, DodgeRules.Tier tier) {
        if (tier.iframeTicks() <= 0) return;
        UUID id = p.getUniqueId();
        iframeUntil.put(id, System.currentTimeMillis() + tier.iframeTicks() * 50L);
        // Registered so quit / death / world change / shutdown always clear the window.
        EffectRegistry.Handle h = plugin.effects().timed(id, "sharingan-dodge", tier.iframeTicks() + 2L, () -> iframeUntil.remove(id));
        if (h == null) iframeUntil.remove(id); // effect cap reached: dash without the avoidance window
    }

    /**
     * Walks up to {@code dist} blocks in 0.5-block steps and returns the furthest safe landing spot, or null if less than a
     * block is available. Each step must have passable feet and head, no liquid, solid non-hazard ground (stepping down at most
     * 2 blocks), stay inside the world border, and have no block between it and the previous step.
     */
    private Location findDestination(Player p, double dirX, double dirZ, double dist) {
        World w = p.getWorld();
        Location start = p.getLocation();
        Location best = null;
        Location prev = start.clone();
        for (double d = 0.5; d <= dist + 1.0e-6; d += 0.5) {
            Location cand = start.clone().add(dirX * d, 0, dirZ * d);
            if (!w.getWorldBorder().isInside(cand)) break;
            Vector seg = cand.toVector().subtract(prev.toVector());
            double len = seg.length();
            if (len > 1.0e-6) {
                RayTraceResult hit = w.rayTraceBlocks(prev.clone().add(0, 1.0, 0), seg.clone().normalize(), len, FluidCollisionMode.NEVER, true);
                if (hit != null) break;
            }
            Location landing = standable(cand);
            if (landing == null) break;
            best = landing;
            prev = cand;
        }
        if (best == null || best.distance(start) < 1.0) return null;
        best.setYaw(start.getYaw());
        best.setPitch(start.getPitch());
        return best;
    }

    /** Returns the standing position at/below {@code cand} (max 2 blocks down) if it is safe, else null. */
    private Location standable(Location cand) {
        for (int dy = 0; dy >= -2; dy--) {
            Block feet = cand.getBlock().getRelative(0, dy, 0);
            Block head = feet.getRelative(0, 1, 0);
            Block ground = feet.getRelative(0, -1, 0);
            if (!feet.isPassable() || feet.isLiquid() || !head.isPassable() || head.isLiquid()) {
                if (dy == 0) return null; // wall at the same level: blocked
                continue;
            }
            if (isHazard(feet.getType()) || isHazard(head.getType())) return null;
            if (ground.isPassable() || ground.isLiquid()) continue; // keep looking down
            if (isHazard(ground.getType())) return null;
            Location out = new Location(cand.getWorld(), cand.getX(), feet.getY(), cand.getZ());
            return out;
        }
        return null;
    }

    private static boolean isHazard(Material m) {
        return switch (m) {
            case LAVA, MAGMA_BLOCK, FIRE, SOUL_FIRE, CAMPFIRE, SOUL_CAMPFIRE, CACTUS, SWEET_BERRY_BUSH, WITHER_ROSE, POWDER_SNOW, COBWEB -> true;
            default -> false;
        };
    }

    /** Crimson silhouette left behind plus a short trail. Bounded particle count; no entities are created. */
    private void afterimage(Location start, Location dest, DodgeRules.Tier tier) {
        World w = start.getWorld();
        boolean mk = tier.key().equals("mangekyo") || tier.key().equals("eternal");
        Particle.DustOptions crimson = new Particle.DustOptions(mk ? Color.fromRGB(0x8a, 0x00, 0x1c) : Color.fromRGB(0xc0, 0x10, 0x20), 1.3f);
        for (double y = 0.1; y <= 1.8; y += 0.3) {
            w.spawnParticle(Particle.DUST, start.clone().add(0, y, 0), 2, 0.18, 0.05, 0.18, 0, crimson);
        }
        w.spawnParticle(Particle.DUST, start.clone().add(0, 1.65, 0), 3, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(Color.WHITE, 0.8f)); // eye flash
        Vector path = dest.toVector().subtract(start.toVector());
        int n = Math.min(12, (int) Math.ceil(path.length() * 2));
        for (int i = 1; i <= n; i++) {
            Location at = start.clone().add(path.clone().multiply(i / (double) n)).add(0, 1.0, 0);
            w.spawnParticle(Particle.DUST, at, 1, 0.1, 0.3, 0.1, 0, crimson);
        }
        if (mk) w.spawnParticle(Particle.REVERSE_PORTAL, start.clone().add(0, 1, 0), 18, 0.3, 0.6, 0.3, 0.05);
    }

    // ------------------------------------------------------------------ input and avoidance

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking() || !enabled() || !plugin.getConfig().getBoolean("sharingan.dodge.double-sneak", true)) return;
        Player p = e.getPlayer();
        Profile pr = plugin.profiles().get(p);
        if (pr.clan != Clan.UCHIHA || !pr.dojutsuActive) return;
        long now = System.currentTimeMillis();
        long window = plugin.getConfig().getLong("sharingan.dodge.double-tap-window-ms", 300L);
        Long last = lastSneak.get(p.getUniqueId());
        if (last != null && DodgeRules.doubleTap(last, now, window)) {
            lastSneak.remove(p.getUniqueId());
            Jutsu j = plugin.jutsus().get("sharingan_dodge");
            if (j != null) plugin.casting().cast(p, pr, j); // same engine path as every other cast
        } else {
            lastSneak.put(p.getUniqueId(), now);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (to == null) return;
        double dx = to.getX() - e.getFrom().getX(), dz = to.getZ() - e.getFrom().getZ();
        if (Math.hypot(dx, dz) < DodgeRules.MOVE_THRESHOLD) return;
        Player p = e.getPlayer();
        if (!plugin.profiles().isLoaded(p.getUniqueId())) return;
        Profile pr = plugin.profiles().get(p);
        if (pr.clan != Clan.UCHIHA || !pr.dojutsuActive) return;
        lastMove.put(p.getUniqueId(), new double[]{dx, dz, System.currentTimeMillis()});
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (iframeUntil.isEmpty() || !(e.getEntity() instanceof Player p)) return;
        Long until = iframeUntil.get(p.getUniqueId());
        if (until == null || !DodgeRules.isInvulnerable(System.currentTimeMillis(), until)) return;
        if (!avoidable().contains(e.getCause())) return; // area attacks, magic, fire, falls... still hurt
        e.setCancelled(true);
        hitsAvoided++;
        p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1, 0), 10, 0.4, 0.6, 0.4, 0.1);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1f, 1.5f);
        Msg.bar(p, "<red>Dodged!");
    }

    private Set<EntityDamageEvent.DamageCause> avoidable() {
        Set<EntityDamageEvent.DamageCause> out = EnumSet.noneOf(EntityDamageEvent.DamageCause.class);
        for (String s : plugin.getConfig().getStringList("sharingan.dodge.avoid-causes")) {
            try {
                out.add(EntityDamageEvent.DamageCause.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("sharingan.dodge.avoid-causes: unknown damage cause '" + s + "'");
            }
        }
        return out;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        lastSneak.remove(id);
        lastMove.remove(id);
        iframeUntil.remove(id);
    }
}
