package dev.shinobi;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Jutsu Book: every technique you can use, with its seal keys. Click one to cast it with no hand seals. */
public final class JutsuBookGui implements Listener {
    private static final class Holder implements InventoryHolder {
        private Inventory inv;
        private final Map<Integer, String> slots = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final ShinobiPlugin plugin;

    public JutsuBookGui(ShinobiPlugin plugin) {
        this.plugin = plugin;
    }

    private static Material iconFor(Jutsu j) {
        if (j.nature == null) return Material.PAPER;
        return switch (j.nature) {
            case FIRE -> Material.BLAZE_POWDER;
            case WATER -> Material.PRISMARINE_SHARD;
            case WIND -> Material.FEATHER;
            case LIGHTNING -> Material.GOLD_NUGGET;
            case EARTH -> Material.CLAY_BALL;
        };
    }

    public void open(Player p) {
        Profile pr = plugin.profiles().get(p);
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 36, Msg.mm("<dark_red>Jutsu Book"));
        h.inv = inv;

        int slot = 0;
        for (Jutsu j : plugin.jutsus().all()) {
            if (!plugin.casting().usable(pr, j) || slot >= inv.getSize()) continue;
            ItemStack it = new ItemStack(iconFor(j));
            ItemMeta m = it.getItemMeta();
            m.displayName(Msg.item("<gold>" + j.name));
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Seals: <white>" + j.sealText());
            lore.add("<gray>Chakra: <aqua>" + (int) Math.ceil(plugin.casting().costFor(pr, j))
                    + " <gray>Cooldown: <white>" + (j.cooldownMs / 1000) + "s");
            lore.add("<green>Click to cast");
            lore.add("<dark_gray>Keybind: /jutsu bind [slot] " + j.id);
            m.lore(lore.stream().map(Msg::item).toList());
            it.setItemMeta(m);
            inv.setItem(slot, it);
            h.slots.put(slot, j.id);
            slot++;
        }
        if (slot == 0) {
            ItemStack none = new ItemStack(Material.BARRIER);
            ItemMeta m = none.getItemMeta();
            m.displayName(Msg.item("<gray>You know no techniques yet."));
            none.setItemMeta(m);
            inv.setItem(13, none);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        String id = h.slots.get(e.getSlot());
        if (id == null) return;
        if (!plugin.casting().easy("quick-cast")) return;
        Jutsu j = plugin.jutsus().get(id);
        if (j == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            p.closeInventory();
            plugin.casting().cast(p, plugin.profiles().get(p), j);
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
