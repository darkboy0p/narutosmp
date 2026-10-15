package dev.shinobi;

import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Tracks every temporary ability effect so it can always be cleaned up.
 *
 * <p>An ability that creates something temporary (a task, an entity, a status, a block...) calls
 * {@link #begin} with a cleanup action. The cleanup runs <b>exactly once</b>, whichever comes first:
 * the effect ends normally ({@link Handle#end()}), the owner quits / dies / changes world,
 * an admin clears it, or the plugin shuts down. Main thread only.
 */
public final class EffectRegistry {
    public enum Reason { EXPIRED, QUIT, DEATH, WORLD_CHANGE, ADMIN, SHUTDOWN, ERROR }

    public final class Handle {
        private final UUID owner;
        private final String tag;
        private final long startedAt = System.currentTimeMillis();
        private Runnable cleanup;
        private BukkitTask task;
        private boolean ended;

        private Handle(UUID owner, String tag, Runnable cleanup) {
            this.owner = owner;
            this.tag = tag;
            this.cleanup = cleanup;
        }

        /** Ties a scheduled task to this effect; it is cancelled when the effect ends. */
        public Handle attach(BukkitTask t) {
            if (ended) t.cancel();
            else this.task = t;
            return this;
        }

        public boolean isEnded() { return ended; }
        public String tag() { return tag; }

        /** Ends the effect normally. Safe to call more than once. */
        public void end() {
            finish(this, Reason.EXPIRED);
        }
    }

    private final ShinobiPlugin plugin;
    private final Map<UUID, List<Handle>> byOwner = new HashMap<>();
    private long totalBegun;
    private long totalCleanedEarly;
    private long cleanupErrors;

    public EffectRegistry(ShinobiPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers a temporary effect. Returns null (and runs nothing) if the owner already has the maximum number
     * of active effects ({@code abilities.max-active-effects-per-player}); callers must then abort the ability.
     */
    public Handle begin(UUID owner, String tag, Runnable cleanup) {
        int max = Math.max(1, plugin.getConfig().getInt("abilities.max-active-effects-per-player", 24));
        List<Handle> list = byOwner.computeIfAbsent(owner, k -> new ArrayList<>());
        if (list.size() >= max) return null;
        Handle h = new Handle(owner, tag, cleanup);
        list.add(h);
        totalBegun++;
        return h;
    }

    /** Registers an effect that ends itself after {@code ticks}. Returns null if over the limit. */
    public Handle timed(UUID owner, String tag, long ticks, Runnable cleanup) {
        Handle h = begin(owner, tag, cleanup);
        if (h == null) return null;
        h.attach(plugin.getServer().getScheduler().runTaskLater(plugin, h::end, Math.max(1L, ticks)));
        return h;
    }

    /** Ends every effect owned by this player. */
    public int cleanup(UUID owner, Reason why) {
        List<Handle> list = byOwner.get(owner);
        if (list == null) return 0;
        int n = 0;
        for (Handle h : new ArrayList<>(list)) {
            finish(h, why);
            n++;
        }
        byOwner.remove(owner);
        return n;
    }

    public int cleanupAll(Reason why) {
        int n = 0;
        for (UUID id : new ArrayList<>(byOwner.keySet())) n += cleanup(id, why);
        return n;
    }

    public int active() {
        int n = 0;
        for (List<Handle> l : byOwner.values()) n += l.size();
        return n;
    }

    public int activeFor(UUID owner) {
        List<Handle> l = byOwner.get(owner);
        return l == null ? 0 : l.size();
    }

    /** One line per active effect, for /shinobi admin debug. */
    public List<String> describe(UUID owner) {
        List<String> out = new ArrayList<>();
        for (Handle h : byOwner.getOrDefault(owner, List.of())) {
            out.add(h.tag + " (" + (System.currentTimeMillis() - h.startedAt) / 1000 + "s)");
        }
        return out;
    }

    public String stats() {
        return "active=" + active() + " begun=" + totalBegun + " cleanedEarly=" + totalCleanedEarly + " cleanupErrors=" + cleanupErrors;
    }

    private void finish(Handle h, Reason why) {
        if (h.ended) return;
        h.ended = true;
        if (why != Reason.EXPIRED) totalCleanedEarly++;
        if (h.task != null) h.task.cancel();
        List<Handle> list = byOwner.get(h.owner);
        if (list != null) {
            list.remove(h);
            if (list.isEmpty()) byOwner.remove(h.owner);
        }
        Runnable r = h.cleanup;
        h.cleanup = null;
        if (r != null) {
            try {
                r.run();
            } catch (RuntimeException e) {
                cleanupErrors++;
                plugin.getLogger().log(Level.SEVERE, "Cleanup of effect '" + h.tag + "' failed (" + why + ")", e);
            }
        }
    }
}
