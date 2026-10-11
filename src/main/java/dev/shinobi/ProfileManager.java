package dev.shinobi;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProfileManager {
    private final ShinobiPlugin plugin;
    private final File dir;
    private final Map<UUID, Profile> cache = new HashMap<>();

    public ProfileManager(ShinobiPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "players");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    public Profile get(Player p) {
        return get(p.getUniqueId());
    }

    public Profile get(UUID id) {
        return cache.computeIfAbsent(id, this::load);
    }

    public boolean isLoaded(UUID id) {
        return cache.containsKey(id);
    }

    public Iterable<Profile> loaded() {
        return new ArrayList<>(cache.values());
    }

    public void unload(UUID id) {
        Profile pr = cache.remove(id);
        if (pr != null) save(pr);
    }

    private File file(UUID id) {
        return new File(dir, id + ".yml");
    }

    private Profile load(UUID id) {
        Profile pr = new Profile(id);
        File f = file(id);
        if (!f.exists()) return pr;
        YamlConfiguration c = YamlConfiguration.loadConfiguration(f);
        pr.rolled = c.getBoolean("rolled", false);
        pr.nature = Nature.parse(c.getString("nature"));
        pr.natureRevealed = c.getBoolean("nature-revealed", false);
        pr.clan = Clan.parse(c.getString("clan"));
        pr.awakened = c.getBoolean("awakened", false);
        pr.maxChakra = c.getDouble("max-chakra", 100.0);
        pr.chakra = pr.maxChakra;
        pr.known.addAll(c.getStringList("known"));
        ConfigurationSection m = c.getConfigurationSection("mastery");
        if (m != null) {
            for (String k : m.getKeys(false)) pr.mastery.put(k, m.getInt(k));
        }
        pr.mangekyo = c.getString("mangekyo");
        pr.strain = c.getDouble("strain", 0.0);
        pr.eternal = c.getBoolean("eternal", false);
        pr.tomoe = c.getInt("tomoe", 0);
        List<String> bl = c.getStringList("binds");
        for (int i = 0; i < 9 && i < bl.size(); i++) pr.binds[i] = bl.get(i) == null || bl.get(i).isBlank() ? null : bl.get(i);
        pr.weightedSeconds = c.getDouble("weighted-seconds", 0.0);
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
        return pr;
    }

    public void save(Profile pr) {
        YamlConfiguration c = new YamlConfiguration();
        c.set("rolled", pr.rolled);
        c.set("nature", pr.nature == null ? null : pr.nature.name());
        c.set("nature-revealed", pr.natureRevealed);
        c.set("clan", pr.clan == null ? null : pr.clan.name());
        c.set("awakened", pr.awakened);
        c.set("max-chakra", pr.maxChakra);
        c.set("known", new ArrayList<>(pr.known));
        for (Map.Entry<String, Integer> e : pr.mastery.entrySet()) c.set("mastery." + e.getKey(), e.getValue());
        c.set("mangekyo", pr.mangekyo);
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
        try {
            c.save(file(pr.id));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save profile " + pr.id + ": " + e.getMessage());
        }
    }

    public void saveAll() {
        for (Profile pr : cache.values()) save(pr);
    }
}
