package dev.shinobi;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owns every active charge. Input is event-driven; one repeating task exists only while at least one charge is active.
 *
 * <p>Controls are server-side only: the charge starts from the existing seal combos / quick-cast, and is released or
 * cancelled by one of the configured triggers (arm swing, swap-hand key, sneak). Paper cannot read arbitrary keys.
 *
 * <p>Chakra is reserved by the {@link AbilityEngine} when the charge starts; this class never touches chakra or
 * cooldowns itself, it only tells the engine how a charge ended. A session leaves the map before it is settled, so a
 * charge can resolve at most once; every session is also registered with the {@link EffectRegistry} so quit, death,
 * world change, admin clear and shutdown always end it.
 */
public final class ChargeManager implements Listener {
    public enum Trigger { SWING, SWAP_HAND, SNEAK }

    private enum Outcome { CANCELLED, FIZZLED, INTERRUPTED, FORCED }

    private final ShinobiPlugin plugin;
    private final ChargeVisuals visuals;
    private final Map<UUID, ChargeSession> sessions = new HashMap<>();
    private final Map<String, ChargeSpec> specs = new LinkedHashMap<>();
    private final Set<Trigger> releaseTriggers = EnumSet.of(Trigger.SWING);
    private final Set<Trigger> cancelTriggers = EnumSet.of(Trigger.SNEAK);
    private BukkitTask task;
    private int interval = 2;

    public ChargeManager(ShinobiPlugin plugin) {
        this.plugin = plugin;
        this.visuals = new ChargeVisuals(plugin);
    }

    // ------------------------------------------------------------------ config

    /** (Re)reads config. Safe to call at any time; active sessions keep the spec they started with. */
    public void reload() {
        interval = Math.max(1, Math.min(5, plugin.getConfig().getInt("charging.tick-interval", 2)));
        releaseTriggers.clear();
        cancelTriggers.clear();
        releaseTriggers.addAll(parseTriggers("charging.release-triggers", EnumSet.of(Trigger.SWING)));
        cancelTriggers.addAll(parseTriggers("charging.cancel-triggers", EnumSet.of(Trigger.SNEAK)));
        if (releaseTriggers.isEmpty()) releaseTriggers.add(Trigger.SWING);
        for (Trigger t : new ArrayList<>(cancelTriggers)) {
            if (releaseTriggers.contains(t)) {
                plugin.getLogger().warning("charging: " + t + " is both a release and a cancel trigger; using it for release only.");
                cancelTriggers.remove(t);
            }
        }
        specs.clear();
        for (Jutsu j : plugin.jutsus().all()) {
            if (j.charge != null) specs.put(j.id, resolve(j));
        }
    }

    private Set<Trigger> parseTriggers(String path, Set<Trigger> fallback) {
        Set<Trigger> out = EnumSet.noneOf(Trigger.class);
        for (String s : plugin.getConfig().getStringList(path)) {
            try {
                out.add(Trigger.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("charging: unknown trigger '" + s + "' in " + path + " (use SWING, SWAP_HAND, SNEAK)");
            }
        }
        return out.isEmpty() && !plugin.getConfig().isList(path) ? EnumSet.copyOf(fallback) : out;
    }

    private ChargeSpec resolve(Jutsu j) {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("charging.jutsu." + j.id);
        if (c == null) return j.charge;
        ChargeSpec.Builder b = j.charge.toBuilder();
        b.minTicks(c.getInt("min-ticks", j.charge.minTicks));
        b.maxTicks(c.getInt("max-ticks", j.charge.maxTicks));
        b.stages(c.getInt("stages", j.charge.stages));
        b.holdLimit(c.getInt("hold-limit-ticks", j.charge.holdLimitTicks));
        b.grace(c.getInt("grace-ticks", j.charge.graceTicks));
        b.slowness(c.getInt("slowness", j.charge.slownessAmp));
        b.cancellable(c.getBoolean("cancellable", j.charge.cancellable));
        try {
            b.earlyRelease(ChargeSpec.EarlyRelease.valueOf(c.getString("early-release", j.charge.earlyRelease.name()).toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("charging.jutsu." + j.id + ".early-release must be FIZZLE or IGNORE");
        }
        b.damageScale(c.getDouble("min-scale", j.charge.minScale), c.getDouble("max-scale", j.charge.maxScale));
        b.rangeScale(c.getDouble("range-min-scale", j.charge.rangeMinScale), c.getDouble("range-max-scale", j.charge.rangeMaxScale));
        return b.build();
    }

    public boolean enabledFor(Jutsu j) {
        if (j.charge == null || j.chargeEffect == null) return false;
        if (!plugin.getConfig().getBoolean("charging.enabled", true)) return false;
        return plugin.getConfig().getBoolean("charging.jutsu." + j.id + ".enabled", true);
    }

    public boolean isCharging(UUID id) {
        return sessions.containsKey(id);
    }

    public int active() {
        return sessions.size();
    }

    // ------------------------------------------------------------------ lifecycle

    /** Starts a session. The engine has already validated the cast and reserved the chakra. Returns false on failure. */
    boolean begin(Player p, Profile pr, Jutsu j, AbilityEngine.Prep prep, ChargeLedger ledger) {
        UUID id = p.getUniqueId();
        if (sessions.containsKey(id)) return false;
        ChargeSpec spec = specs.getOrDefault(j.id, j.charge);
        ChargeSession s = new ChargeSession(id, p.getWorld().getUID(), j, spec, ledger, prep.cooldownMs(), prep.copied());
        EffectRegistry.Handle h = plugin.effects().begin(id, "charge:" + j.id, () -> {
            ChargeSession cur = sessions.get(id);
            if (cur == s) end(s, Outcome.FORCED, null);
        });
        if (h == null) {
            Msg.bar(p, "<red>Too many active effects.");
            return false;
        }
        s.handle = h;
        sessions.put(id, s);
        ensureTask();
        visuals.sound(p, spec.style, ChargeVisuals.Cue.START, 0);
        feedback(p, s);
        return true;
    }

    private void ensureTask() {
        if (task != null) return;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    private void tick() {
        if (sessions.isEmpty()) {
            if (task != null) task.cancel();
            task = null;
            return;
        }
        double budget = Math.min(1.0, plugin.getConfig().getInt("charging.visuals.session-budget", 12) / (double) sessions.size());
        boolean autoRelease = !"CANCEL".equalsIgnoreCase(plugin.getConfig().getString("charging.overcharge-action", "RELEASE"));
        for (ChargeSession s : new ArrayList<>(sessions.values())) {
            if (sessions.get(s.owner) != s) continue;
            Player p = Bukkit.getPlayer(s.owner);
            if (p == null || !p.isOnline() || p.isDead() || p.getGameMode() == GameMode.SPECTATOR
                    || !p.getWorld().getUID().equals(s.worldId)) {
                end(s, Outcome.FORCED, p);
                continue;
            }
            s.elapsed += interval;
            applySlowness(p, s);

            int stage = s.spec.stageOf(s.elapsed);
            if (stage > s.lastStage) {
                s.lastStage = stage;
                if (s.spec.full(s.elapsed)) {
                    s.fullAnnounced = true;
                    visuals.sound(p, s.spec.style, ChargeVisuals.Cue.FULL, stage);
                } else {
                    visuals.sound(p, s.spec.style, ChargeVisuals.Cue.STAGE, stage);
                }
            }
            if (s.elapsed % 10 < interval && s.spec.ready(s.elapsed)) visuals.sound(p, s.spec.style, ChargeVisuals.Cue.LOOP, stage);
            visuals.tick(p, s, budget);
            visuals.orb(p, s);
            feedback(p, s);

            ChargeSpec.Tick t = s.spec.onTick(s.elapsed, autoRelease);
            if (t == ChargeSpec.Tick.AUTO_RELEASE) release(s, p);
            else if (t == ChargeSpec.Tick.AUTO_CANCEL) end(s, Outcome.CANCELLED, p);
        }
    }

    private void applySlowness(Player p, ChargeSession s) {
        if (s.spec.slownessAmp < 0) return;
        // Short, self-expiring effect refreshed every tick: it can never outlive the charge by more than a moment, even after a crash.
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, interval + 8, s.spec.slownessAmp, true, false, false));
    }

    private void clearSlowness(Player p, ChargeSession s) {
        if (p == null || s.spec.slownessAmp < 0) return;
        PotionEffect cur = p.getPotionEffect(PotionEffectType.SLOWNESS);
        if (cur != null && cur.getAmplifier() == s.spec.slownessAmp && cur.getDuration() <= interval + 8) {
            p.removePotionEffect(PotionEffectType.SLOWNESS);
        }
    }

    // ------------------------------------------------------------------ release / end

    /** Resolves a release. The session is removed first, so this can only ever run once per charge. */
    private void release(ChargeSession s, Player p) {
        if (!sessions.remove(s.owner, s)) return;
        visuals.removeOrb(s.owner);
        clearSlowness(p, s);
        Profile pr = plugin.profiles().get(s.owner);
        boolean fired = plugin.abilities().settleRelease(p, pr, s);
        if (fired) {
            visuals.sound(p, s.spec.style, ChargeVisuals.Cue.RELEASE, s.spec.stageOf(s.elapsed));
            Msg.bar(p, "<gold>" + s.jutsu.name + "!" + (s.spec.full(s.elapsed) ? " <yellow>(full charge)" : ""));
        } else {
            visuals.sound(p, s.spec.style, ChargeVisuals.Cue.FIZZLE, 0);
            Msg.bar(p, "<red>" + s.jutsu.name + ": no valid target. Chakra partly refunded.");
        }
        s.handle.end();
    }

    private void end(ChargeSession s, Outcome why, Player p) {
        if (!sessions.remove(s.owner, s)) return;
        visuals.removeOrb(s.owner);
        clearSlowness(p, s);
        Profile pr = plugin.profiles().get(s.owner);
        String key = switch (why) {
            case CANCELLED -> "cancelled";
            case FIZZLED -> "fizzled";
            case INTERRUPTED -> "interrupted";
            case FORCED -> "forced";
        };
        plugin.abilities().settleCancel(p, pr, s, key);
        if (p != null && why != Outcome.FORCED) {
            visuals.sound(p, s.spec.style, why == Outcome.FIZZLED ? ChargeVisuals.Cue.FIZZLE : ChargeVisuals.Cue.CANCEL, 0);
            Msg.bar(p, switch (why) {
                case CANCELLED -> "<gray>" + s.jutsu.name + " cancelled.";
                case FIZZLED -> "<red>Released too early: the chakra scatters.";
                case INTERRUPTED -> "<red>Charge interrupted!";
                default -> "";
            });
        }
        s.handle.end();
    }

    /** Admin/debug: ends a player's charge without any message. */
    public void clear(UUID id) {
        ChargeSession s = sessions.get(id);
        if (s != null) end(s, Outcome.FORCED, Bukkit.getPlayer(id));
    }

    // ------------------------------------------------------------------ feedback

    private void feedback(Player p, ChargeSession s) {
        if (!plugin.getConfig().getBoolean("charging.feedback.actionbar", true)) return;
        int cells = 10;
        int filled = (int) Math.round(s.spec.fraction(s.elapsed) * cells);
        int minCell = (int) Math.round(s.spec.minTicks / (double) s.spec.maxTicks * cells);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < cells; i++) {
            String color = i >= filled ? "<dark_gray>" : (s.spec.full(s.elapsed) ? "<gold>" : (i >= minCell ? "<green>" : "<aqua>"));
            bar.append(color).append(i >= filled ? "\u25B1" : "\u25B0");
        }
        String state;
        if (s.spec.full(s.elapsed)) {
            int left = Math.max(0, (s.spec.maxTicks + s.spec.holdLimitTicks - s.elapsed) / 20);
            state = "<gold><bold>FULL<reset> <gray>- " + releaseHint() + " <dark_gray>(" + left + "s)";
        } else if (s.spec.ready(s.elapsed)) {
            state = "<green>Ready <gray>- " + releaseHint();
        } else {
            state = "<gray>Gathering chakra...";
        }
        String cancel = s.spec.cancellable && !cancelTriggers.isEmpty() ? " <dark_gray>| " + label(cancelTriggers) + " cancels" : "";
        Msg.bar(p, "<aqua>" + s.jutsu.name + " " + bar + " <white>" + s.spec.stageOf(s.elapsed) + "/" + s.spec.stages + " " + state + cancel);
    }

    private String releaseHint() {
        return label(releaseTriggers) + " to release";
    }

    private static String label(Set<Trigger> t) {
        StringBuilder sb = new StringBuilder();
        for (Trigger x : t) {
            if (sb.length() > 0) sb.append("/");
            sb.append(switch (x) {
                case SWING -> "swing";
                case SWAP_HAND -> "F";
                case SNEAK -> "sneak";
            });
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ input

    private void trigger(Player p, Trigger t) {
        ChargeSession s = sessions.get(p.getUniqueId());
        if (s == null) return;
        if (releaseTriggers.contains(t)) {
            switch (s.spec.decideRelease(s.elapsed)) {
                case IGNORE -> { }
                case FIZZLE -> end(s, Outcome.FIZZLED, p);
                case RELEASE -> release(s, p);
            }
        } else if (cancelTriggers.contains(t)) {
            if (s.elapsed < s.spec.graceTicks) return;
            if (!s.spec.cancellable) {
                Msg.bar(p, "<red>This technique cannot be cancelled.");
                return;
            }
            end(s, Outcome.CANCELLED, p);
        }
    }

    /** Called by SealManager for the swap-hand key while a charge is active. */
    void onSwap(Player p) {
        trigger(p, Trigger.SWAP_HAND);
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent e) {
        if (!sessions.isEmpty()) trigger(e.getPlayer(), Trigger.SWING);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (e.isSneaking() && !sessions.isEmpty()) trigger(e.getPlayer(), Trigger.SNEAK);
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p) interrupt(p);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        interrupt(e.getPlayer());
    }

    @EventHandler
    public void onGameMode(PlayerGameModeChangeEvent e) {
        interrupt(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (sessions.isEmpty() || !(e.getEntity() instanceof Player p)) return;
        double threshold = plugin.getConfig().getDouble("charging.interrupt-damage-threshold", 4.0);
        if (threshold >= 0 && e.getFinalDamage() >= threshold && e.getFinalDamage() > 0) interrupt(p);
    }

    private void interrupt(Player p) {
        ChargeSession s = sessions.get(p.getUniqueId());
        if (s != null) end(s, Outcome.INTERRUPTED, p);
    }

    /** Plugin shutdown: sessions are ended by the effect registry first; this removes any orb entity left over. */
    public void shutdown() {
        visuals.removeAllOrbs();
    }

    public int orbs() {
        return visuals.orbCount();
    }

    public String stats() {
        return "active=" + sessions.size() + " orbs=" + visuals.orbCount() + " specs=" + specs.size() + " tickInterval=" + interval
                + " release=" + releaseTriggers + " cancel=" + cancelTriggers;
    }
}
