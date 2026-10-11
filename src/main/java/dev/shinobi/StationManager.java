package dev.shinobi;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tracks placed Scroll Desks and Mission Boards (they are ordinary blocks with a custom role). */
public final class StationManager implements Listener {
    private final ShinobiPlugin plugin;
    private final File file;
    private final Map<String, String> stations = new HashMap<>(); // "world;x;y;z" -> type id

    public StationManager(ShinobiPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stations.yml");
        load();
    }

    private static String key(Block b) {
        return b.getWorld().getUID() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    private static Material materialFor(String type) {
        return type.equals("scroll_desk") ? Material.LECTERN : Material.CARTOGRAPHY_TABLE;
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
        for (String s : c.getStringList("stations")) {
            int i = s.lastIndexOf('|');
            if (i > 0) stations.put(s.substring(0, i), s.substring(i + 1));
        }
    }

    public void save() {
        YamlConfiguration c = new YamlConfiguration();
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : stations.entrySet()) out.add(e.getKey() + "|" + e.getValue());
        c.set("stations", out);
        try {
            c.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save stations.yml: " + e.getMessage());
        }
    }

    /** Returns the station type at this block, or null. Stale entries are dropped. */
    public String typeAt(Block b) {
        String t = stations.get(key(b));
        if (t == null) return null;
        if (b.getType() != materialFor(t)) {
            stations.remove(key(b));
            return null;
        }
        return t;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        String tag = plugin.items().tag(e.getItemInHand());
        if ("scroll_desk".equals(tag) || "mission_board".equals(tag)) {
            stations.put(key(e.getBlockPlaced()), tag);
            save();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        String t = typeAt(b);
        if (t == null) return;
        stations.remove(key(b));
        save();
        e.setDropItems(false);
        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
            ItemManager.Type type = ItemManager.Type.byId(t);
            if (type != null) {
                b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), plugin.items().create(type, 1));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        if (e.getPlayer().isSneaking()) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        String t = typeAt(b);
        if (t == null) return;
        e.setCancelled(true);
        if (t.equals("scroll_desk")) plugin.deskGui().open(e.getPlayer());
        else plugin.boardGui().open(e.getPlayer(), b.getLocation());
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> typeAt(b) != null);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> typeAt(b) != null);
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) {
            if (typeAt(b) != null) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) {
            if (typeAt(b) != null) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
