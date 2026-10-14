package dev.shinobi;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Loads and saves player profiles.
 *
 * <ul>
 *   <li>Files are versioned ({@link SchemaMigrator}); a pre-migration copy is kept in
 *       {@code players/backup/pre-schema-N/} and never overwritten.</li>
 *   <li>Writes go to a temp file and are moved into place, so a crash cannot leave a half-written profile.
 *       The first save of each session also keeps the previous file as {@code <uuid>.yml.bak}.</li>
 *   <li>YAML is serialised on the main thread; disk I/O runs on one ordered background thread.</li>
 *   <li>Unknown keys are preserved, so a newer or hand-edited file is never silently stripped.</li>
 *   <li>A corrupt file is renamed to {@code *.corrupt-<time>} (never deleted) and the {@code .bak} is tried.</li>
 * </ul>
 */
public final class ProfileManager {
    private static final Set<String> KNOWN = Set.of(
            SchemaMigrator.KEY_VERSION, "rolled", "nature", "nature-revealed", "clan", "legacy-clan", "clan-history",
            "awakened", "max-chakra", "known", "mastery", "mangekyo", "strain", "eternal", "tomoe", "binds",
            "weighted-seconds", "completed-missions", "missions", "bonds", "bloodlines", "forced-bloodlines", "forced-abilities", "mangekyo-powers", "headband-worn");

    private final ShinobiPlugin plugin;
    private final File dir;
    private final File backupDir;
    private final Map<UUID, Profile> cache = new HashMap<>();

    /** Serialised-but-not-yet-written data, so a quick rejoin never reads a stale file. */
    private final Map<UUID, String> pending = new ConcurrentHashMap<>();
    private final Set<UUID> bakDone = ConcurrentHashMap.newKeySet();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ShinobiSMP-IO");
        t.setDaemon(false);
        return t;
    });
    private volatile int writeFailures;

    public ProfileManager(ShinobiPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "players");
        this.backupDir = new File(dir, "backup");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    public Profile get(Player p) {
        return get(p.getUniqueId());
    }

    public Profile get(UUID id) {
        return cache.computeIfAbsent(id, this::load);
    }

    /** True if a profile file (or a queued write) exists. Used so admin tools never create profiles for unknown UUIDs. */
    public boolean exists(UUID id) {
        return cache.containsKey(id) || pending.containsKey(id) || file(id).exists();
    }

    public boolean isLoaded(UUID id) {
        return cache.containsKey(id);
    }

    public Iterable<Profile> loaded() {
        return new ArrayList<>(cache.values());
    }

    public int loadedCount() {
        return cache.size();
    }

    public int writeFailures() {
        return writeFailures;
    }

    public int pendingWrites() {
        return pending.size();
    }

    public void unload(UUID id) {
        Profile pr = cache.remove(id);
        if (pr != null) save(pr);
    }

    private File file(UUID id) {
        return new File(dir, id + ".yml");
    }

    private File bakFile(UUID id) {
        return new File(dir, id + ".yml.bak");
    }

    // ------------------------------------------------------------------ load

    private Profile load(UUID id) {
        bakDone.remove(id); // first save of this session keeps a fresh .bak
        Profile pr = new Profile(id);
        File f = file(id);
        String queued = pending.get(id);
        YamlConfiguration c = null;
        boolean fromBak = false;

        if (queued != null) c = parse(queued);
        if (c == null) {
            if (!f.exists()) return pr; // brand-new player
            c = parse(f);
            if (c == null) {
                quarantine(f);
                File bak = bakFile(id);
                if (bak.exists()) {
                    c = parse(bak);
                    fromBak = c != null;
                }
                if (c == null) {
                    plugin.getLogger().severe("Profile " + id + " was corrupt and no usable .bak exists. "
                            + "The damaged file was kept as *.corrupt-*; the player starts fresh.");
                    return pr;
                }
                plugin.getLogger().warning("Profile " + id + " was corrupt; restored from " + bak.getName() + ".");
            }
        }

        int version = SchemaMigrator.versionOf(doc(c));
        boolean migrated = false;
        if (version > SchemaMigrator.CURRENT) {
            pr.readOnly = true;
            plugin.getLogger().warning("Profile " + id + " has schema " + version + " (this plugin supports "
                    + SchemaMigrator.CURRENT + "). Loaded read-only; changes will NOT be saved.");
        } else if (version < SchemaMigrator.CURRENT) {
            if (backupBeforeMigration(id, f, c)) {
                migrated = SchemaMigrator.migrate(doc(c));
                plugin.getLogger().info("Migrated profile " + id + " from schema " + version + " to " + SchemaMigrator.CURRENT + ".");
            } else {
                // Could not take the safety copy: run on the in-memory migrated view but never write it.
                SchemaMigrator.migrate(doc(c));
                pr.readOnly = true;
                plugin.getLogger().severe("Could not back up profile " + id + " before migration; loaded read-only.");
            }
        }
        read(pr, c);
        if ((migrated || fromBak) && !pr.readOnly) save(pr);
        return pr;
    }

    private void read(Profile pr, YamlConfiguration c) {
        pr.schemaVersion = SchemaMigrator.versionOf(doc(c));
        pr.rolled = c.getBoolean("rolled", false);
        pr.nature = Nature.parse(c.getString("nature"));
        pr.natureRevealed = c.getBoolean("nature-revealed", false);
        String rawClan = c.getString("clan");
        pr.clan = Clan.parse(rawClan);
        pr.unknownClan = (pr.clan == null && rawClan != null && !rawClan.isBlank()) ? rawClan : null;
        if (pr.unknownClan != null) plugin.getLogger().warning("Profile " + pr.id + " has unknown clan '" + rawClan + "'; kept untouched.");
        pr.legacyClan = c.getString("legacy-clan");
        pr.clanHistory.addAll(c.getStringList("clan-history"));
        for (String b : c.getStringList("forced-bloodlines")) { String id = AdminInput.id(b); if (id != null) pr.forcedBloodlines.add(id); }
        for (String b : c.getStringList("forced-abilities")) { String id = AdminInput.id(b); if (id != null) pr.forcedAbilities.add(id); }
        for (String b : c.getStringList("bloodlines")) if (b != null && !b.isBlank()) pr.bloodlines.add(b.toLowerCase(Locale.ROOT));
        pr.awakened = c.getBoolean("awakened", false);
        pr.maxChakra = Math.max(1.0, c.getDouble("max-chakra", 100.0));
        pr.chakra = pr.maxChakra;
        pr.known.addAll(c.getStringList("known"));
        ConfigurationSection m = c.getConfigurationSection("mastery");
        if (m != null) {
            for (String k : m.getKeys(false)) pr.mastery.put(k, Math.max(0, m.getInt(k)));
        }
        pr.headbandWorn = c.getBoolean("headband-worn", false);
        pr.mangekyo = c.getString("mangekyo");
        for (String mp : c.getStringList("mangekyo-powers")) if (MangekyoManager.ABILITIES.contains(mp)) pr.mangekyoPowers.add(mp);
        if (pr.mangekyo != null && MangekyoManager.ABILITIES.contains(pr.mangekyo)) pr.mangekyoPowers.add(pr.mangekyo); // legacy single power
        pr.strain = Math.max(0.0, c.getDouble("strain", 0.0));
        pr.eternal = c.getBoolean("eternal", false);
        pr.tomoe = Math.max(0, Math.min(3, c.getInt("tomoe", 0)));
        List<String> bl = c.getStringList("binds");
        for (int i = 0; i < 9 && i < bl.size(); i++) pr.binds[i] = bl.get(i) == null || bl.get(i).isBlank() ? null : bl.get(i);
        pr.weightedSeconds = Math.max(0.0, c.getDouble("weighted-seconds", 0.0));
        pr.completedMissions.addAll(c.getStringList("completed-missions"));
        for (Map<?, ?> mm : c.getMapList("missions")) {
            MissionState ms = MissionState.fromMap(mm);
            if (ms != null) pr.missions.add(ms);
        }
        for (String s : c.getStringList("bonds")) {
            try {
                pr.bonds.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (String key : c.getKeys(false)) {
            if (!KNOWN.contains(key)) pr.extra.put(key, plain(c.get(key)));
        }
    }

    private YamlConfiguration parse(File f) {
        try {
            YamlConfiguration c = new YamlConfiguration();
            c.loadFromString(Files.readString(f.toPath(), StandardCharsets.UTF_8));
            return c;
        } catch (IOException | InvalidConfigurationException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Cannot read " + f.getName() + ": " + e.getMessage());
            return null;
        }
    }

    private YamlConfiguration parse(String text) {
        try {
            YamlConfiguration c = new YamlConfiguration();
            c.loadFromString(text);
            return c;
        } catch (InvalidConfigurationException | RuntimeException e) {
            return null;
        }
    }

    private void quarantine(File f) {
        File dest = new File(f.getParentFile(), f.getName() + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.move(f.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not quarantine corrupt " + f.getName(), e);
        }
    }

    /** Copies the untouched original into backup/pre-schema-N/. Existing backups are never replaced. */
    private boolean backupBeforeMigration(UUID id, File original, YamlConfiguration c) {
        if (!original.exists()) return true; // data came from the pending queue; nothing on disk to protect
        File target = new File(backupDir, "pre-schema-" + SchemaMigrator.CURRENT + "/" + id + ".yml");
        if (target.exists()) return true;
        try {
            Files.createDirectories(target.getParentFile().toPath());
            Files.copy(original.toPath(), target.toPath());
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Backup failed for " + id, e);
            return false;
        }
    }

    // ------------------------------------------------------------------ save

    /** Serialises now (main thread) and writes in the background. Never throws. */
    public void save(Profile pr) {
        if (pr.readOnly) return;
        String data;
        try {
            data = serialize(pr);
        } catch (RuntimeException e) {
            writeFailures++;
            plugin.getLogger().log(Level.SEVERE, "Could not serialise profile " + pr.id + " (file left untouched)", e);
            return;
        }
        pending.put(pr.id, data);
        final String payload = data;
        try {
            io.execute(() -> write(pr.id, payload));
        } catch (RuntimeException e) { // executor already shut down: write inline
            write(pr.id, payload);
        }
    }

    public void saveAll() {
        for (Profile pr : new ArrayList<>(cache.values())) save(pr);
    }

    /** Stops accepting async work and waits for queued writes. Call from onDisable after the final saveAll(). */
    public void close() {
        io.shutdown();
        try {
            if (!io.awaitTermination(15, TimeUnit.SECONDS)) {
                plugin.getLogger().severe("Profile writer did not finish in 15s; " + pending.size() + " write(s) may be lost.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String serialize(Profile pr) {
        YamlConfiguration c = new YamlConfiguration();
        for (Map.Entry<String, Object> e : pr.extra.entrySet()) c.set(e.getKey(), e.getValue());
        c.set(SchemaMigrator.KEY_VERSION, SchemaMigrator.CURRENT);
        c.set("rolled", pr.rolled);
        c.set("nature", pr.nature == null ? null : pr.nature.name());
        c.set("nature-revealed", pr.natureRevealed);
        c.set("clan", pr.clan != null ? pr.clan.name() : pr.unknownClan);
        c.set("legacy-clan", pr.legacyClan);
        c.set("clan-history", new ArrayList<>(pr.clanHistory));
        c.set("bloodlines", new ArrayList<>(pr.bloodlines));
        c.set("forced-bloodlines", new ArrayList<>(pr.forcedBloodlines));
        c.set("forced-abilities", new ArrayList<>(pr.forcedAbilities));
        c.set("awakened", pr.awakened);
        c.set("max-chakra", pr.maxChakra);
        c.set("known", new ArrayList<>(pr.known));
        for (Map.Entry<String, Integer> e : pr.mastery.entrySet()) c.set("mastery." + e.getKey(), e.getValue());
        c.set("headband-worn", pr.headbandWorn);
        c.set("mangekyo", pr.mangekyo);
        c.set("mangekyo-powers", new ArrayList<>(pr.mangekyoPowers));
        c.set("strain", pr.strain);
        c.set("eternal", pr.eternal);
        c.set("tomoe", pr.tomoe);
        List<String> bl = new ArrayList<>();
        for (String b : pr.binds) bl.add(b == null ? "" : b);
        c.set("binds", bl);
        c.set("weighted-seconds", pr.weightedSeconds);
        List<String> comp = new ArrayList<>(pr.completedMissions);
        if (comp.size() > 40) comp = new ArrayList<>(comp.subList(comp.size() - 40, comp.size()));
        c.set("completed-missions", comp);
        List<Map<String, Object>> ms = new ArrayList<>();
        for (MissionState m : pr.missions) ms.add(m.toMap());
        c.set("missions", ms);
        List<String> bonds = new ArrayList<>();
        for (UUID u : pr.bonds) bonds.add(u.toString());
        c.set("bonds", bonds);
        return c.saveToString();
    }

    /** Runs on the IO thread. */
    private void write(UUID id, String data) {
        Path target = file(id).toPath();
        Path tmp = new File(dir, id + ".yml.tmp").toPath();
        try {
            Files.createDirectories(dir.toPath());
            if (Files.exists(target) && bakDone.add(id)) {
                Files.copy(target, bakFile(id).toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(tmp, data, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            pending.remove(id, data);
        } catch (IOException | RuntimeException e) {
            writeFailures++;
            plugin.getLogger().log(Level.SEVERE, "Could not save profile " + id + " (previous file is intact; will retry on next save)", e);
        }
    }

    // ------------------------------------------------------------------ admin recovery

    /** Restores {@code <uuid>.yml.bak} over the profile file. Player must be offline. Returns a status message. */
    public String restoreBackup(UUID id) {
        if (cache.containsKey(id)) return "Player is online; ask them to leave first.";
        File bak = bakFile(id);
        if (!bak.exists()) return "No .bak file for " + id + ".";
        try {
            File f = file(id);
            if (f.exists()) Files.copy(f.toPath(), new File(dir, id + ".yml.before-restore-" + System.currentTimeMillis()).toPath());
            Files.copy(bak.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return "Restored " + id + " from .bak (previous file kept as *.before-restore-*).";
        } catch (IOException e) {
            return "Restore failed: " + e.getMessage();
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Adapter so the pure SchemaMigrator can work on a YamlConfiguration. */
    private static SchemaMigrator.Doc doc(YamlConfiguration c) {
        return new SchemaMigrator.Doc() {
            @Override public String getString(String key) { return c.getString(key); }
            @Override public boolean getBoolean(String key, boolean def) { return c.getBoolean(key, def); }
            @Override public int getInt(String key, int def) { return c.getInt(key, def); }
            @Override public List<String> getStringList(String key) { return c.getStringList(key); }
            @Override public void set(String key, Object value) { c.set(key, value); }
        };
    }

    private static Object plain(Object o) {
        if (o instanceof ConfigurationSection cs) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (String k : cs.getKeys(false)) m.put(k, plain(cs.get(k)));
            return m;
        }
        return o;
    }
}
