package dev.shinobi;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Temporary blocks (jutsu walls) that always restore the original block.
 *
 * <p>Crash safety: every placed block is recorded in {@code tempblocks.yml} (world, position, original block data,
 * the material we placed, and an absolute expiry time). The journal is written within one tick of a change.
 * On startup anything left in the journal is restored. A block is only restored if it is still the material we placed,
 * so player builds that replaced it are never overwritten. Explosions and pistons cannot move or destroy tracked blocks.
 * Region-protection checks are NOT part of Stage 1.
 */
public final class TempBlocks implements Listener {
    private record Key(UUID world, int x, int y, int z) {}

    private static final class Entry {
        final String original;   // BlockData string of what was there before
        final Material placed;
        final long expireAt;     // epoch millis

        Entry(String original, Material placed, long expireAt) {
            this.original = original;
            this.placed = placed;
            this.expireAt = expireAt;
        }
    }

    private final ShinobiPlugin plugin;
    private final File journal;
    private final Map<Key, Entry> entries = new HashMap<>();
    private BukkitTask sweeper;
    private boolean dirty;
    private boolean flushQueued;
    private long restoredTotal;

    public TempBlocks(ShinobiPlugin plugin) {
        this.plugin = plugin;
        this.journal = new File(plugin.getDataFolder(), "tempblocks.yml");
    }

    /** Call once from onEnable: restores anything a previous crash left behind and starts the expiry sweep. */
    public void start() {
        loadJournal();
        if (!entries.isEmpty()) {
            plugin.getLogger().warning("Restoring " + entries.size() + " temporary block(s) left from the previous run.");
            restoreAll();
        }
        sweeper = plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweep, 10L, 10L);
    }

    public int count() {
        return entries.size();
    }

    public long restoredTotal() {
        return restoredTotal;
    }

    public boolean isTracked(Block b) {
        return entries.containsKey(keyOf(b));
    }

    public boolean place(Block b, Material mat, long ticks) {
        if (b.isLiquid() || !(b.getType().isAir() || b.isReplaceable())) return false;
        int cap = Math.max(1, plugin.getConfig().getInt("temp-blocks.max-total", 2000));
        if (entries.size() >= cap) return false;
        Key key = keyOf(b);
        if (entries.containsKey(key)) return false;

        long maxSec = Math.max(1, plugin.getConfig().getLong("temp-blocks.max-seconds", 300));
        long millis = Math.min(Math.max(1L, ticks) * 50L, maxSec * 1000L);
        entries.put(key, new Entry(b.getBlockData().getAsString(), mat, System.currentTimeMillis() + millis));
        b.setType(mat, false);
        markDirty();
        return true;
    }

    // ------------------------------------------------------------------ restore

    private void sweep() {
        if (entries.isEmpty()) return;
        long now = System.currentTimeMillis();
        int budget = 500;
        for (Map.Entry<Key, Entry> e : new ArrayList<>(entries.entrySet())) {
            if (e.getValue().expireAt > now) continue;
            if (budget-- <= 0) break;
            revert(e.getKey(), e.getValue());
        }
    }

    /** Restores one entry. Keeps it (returns false) only if its world is not loaded yet. */
    private boolean revert(Key key, Entry en) {
        World w = Bukkit.getWorld(key.world());
        if (w == null) return false;
        entries.remove(key);
        markDirty();
        Block b = w.getBlockAt(key.x(), key.y(), key.z());
        if (b.getType() != en.placed) return true; // someone replaced it: leave their change alone
        try {
            BlockData original = Bukkit.createBlockData(en.original);
            b.setBlockData(original, false);
            restoredTotal++;
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Bad stored block data '" + en.original + "'; replaced with air at "
                    + key.x() + "," + key.y() + "," + key.z());
            b.setType(Material.AIR, false);
        }
        return true;
    }

    private void restoreAll() {
        for (Map.Entry<Key, Entry> e : new ArrayList<>(entries.entrySet())) revert(e.getKey(), e.getValue());
        flushNow();
    }

    /** Call from onDisable: restores every block and writes the journal. */
    public void shutdown() {
        if (sweeper != null) sweeper.cancel();
        restoreAll();
        if (!entries.isEmpty()) {
            plugin.getLogger().warning(entries.size() + " temporary block(s) are in worlds that are not loaded; "
                    + "they stay in tempblocks.yml and are restored when that world loads.");
        }
    }

    // ------------------------------------------------------------------ journal

    private static Key keyOf(Block b) {
        return new Key(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
    }

    private void markDirty() {
        dirty = true;
        if (flushQueued || !plugin.isEnabled()) return;
        flushQueued = true;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            flushQueued = false;
            flushNow();
        });
    }

    private void flushNow() {
        if (!dirty) return;
        dirty = false;
        Path target = journal.toPath();
        try {
            if (entries.isEmpty()) {
                Files.deleteIfExists(target);
                return;
            }
            YamlConfiguration c = new YamlConfiguration();
            List<Map<String, Object>> list = new ArrayList<>();
            for (Map.Entry<Key, Entry> e : entries.entrySet()) {
                Map<String, Object> m = new HashMap<>();
                m.put("world", e.getKey().world().toString());
                m.put("x", e.getKey().x());
                m.put("y", e.getKey().y());
                m.put("z", e.getKey().z());
                m.put("original", e.getValue().original);
                m.put("placed", e.getValue().placed.name());
                m.put("expire", e.getValue().expireAt);
                list.add(m);
            }
            c.set("entries", list);
            Files.createDirectories(target.getParent());
            Path tmp = new File(journal.getParentFile(), "tempblocks.yml.tmp").toPath();
            Files.writeString(tmp, c.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            dirty = true;
            plugin.getLogger().log(Level.SEVERE, "Could not write tempblocks.yml (blocks cannot be recovered after a crash!)", ex);
        }
    }

    private void loadJournal() {
        if (!journal.exists()) return;
        YamlConfiguration c = new YamlConfiguration();
        try {
            c.loadFromString(Files.readString(journal.toPath(), StandardCharsets.UTF_8));
        } catch (Exception ex) {
            File bad = new File(journal.getParentFile(), "tempblocks.yml.corrupt-" + System.currentTimeMillis());
            //noinspection ResultOfMethodCallIgnored
            journal.renameTo(bad);
            plugin.getLogger().severe("tempblocks.yml was unreadable and was moved to " + bad.getName()
                    + ". Any leftover jutsu walls must be removed manually.");
            return;
        }
        for (Map<?, ?> m : c.getMapList("entries")) {
            try {
                UUID world = UUID.fromString(String.valueOf(m.get("world")));
                int x = ((Number) m.get("x")).intValue();
                int y = ((Number) m.get("y")).intValue();
                int z = ((Number) m.get("z")).intValue();
                String original = String.valueOf(m.get("original"));
                Material placed = Material.valueOf(String.valueOf(m.get("placed")));
                long expire = ((Number) m.get("expire")).longValue();
                entries.put(new Key(world, x, y, z), new Entry(original, placed, expire));
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Skipped a malformed tempblocks.yml entry: " + m);
            }
        }
        dirty = false;
    }

    // ------------------------------------------------------------------ listeners

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        UUID id = e.getWorld().getUID();
        boolean any = false;
        for (Map.Entry<Key, Entry> en : new ArrayList<>(entries.entrySet())) {
            if (en.getKey().world().equals(id)) {
                revert(en.getKey(), en.getValue());
                any = true;
            }
        }
        if (any) flushNow();
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Key key = keyOf(e.getBlock());
        if (entries.containsKey(key)) {
            e.setDropItems(false);
            entries.remove(key); // player broke it: leave it as air
            markDirty();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(this::isTracked);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(this::isTracked);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) {
            if (isTracked(b)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) {
            if (isTracked(b)) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
