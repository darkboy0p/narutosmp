package dev.shinobi;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Temporary blocks (jutsu walls) that restore the original block after a delay. */
public final class TempBlocks implements Listener {
    private final ShinobiPlugin plugin;
    private final Map<Location, BlockData> originals = new HashMap<>();

    public TempBlocks(ShinobiPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean place(Block b, Material mat, long ticks) {
        if (b.isLiquid() || !(b.getType().isAir() || b.isReplaceable())) return false;
        Location key = b.getLocation();
        if (originals.containsKey(key)) return false;
        originals.put(key, b.getBlockData());
        b.setType(mat, false);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> revert(key), ticks);
        return true;
    }

    private void revert(Location key) {
        BlockData original = originals.remove(key);
        if (original != null) key.getBlock().setBlockData(original, false);
    }

    public void revertAll() {
        for (Location k : new ArrayList<>(originals.keySet())) revert(k);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Location key = e.getBlock().getLocation();
        if (originals.containsKey(key)) {
            e.setDropItems(false);
            originals.remove(key); // player broke it: leave it as air
        }
    }
}
