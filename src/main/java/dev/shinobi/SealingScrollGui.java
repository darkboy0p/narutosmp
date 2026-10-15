package dev.shinobi;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.List;

/**
 * Sealing Scroll: portable storage. While open, the scroll is taken out of your hand and held by this menu;
 * on close, the contents are sealed back into it and it is returned to you (this prevents duplication).
 */
public final class SealingScrollGui implements Listener {
    private static final class Holder implements InventoryHolder {
        private Inventory inv;
        private ItemStack scroll;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final ShinobiPlugin plugin;
    private final NamespacedKey storeKey;

    public SealingScrollGui(ShinobiPlugin plugin) {
        this.plugin = plugin;
        this.storeKey = new NamespacedKey(plugin, "sealed_items");
    }

    public void open(Player p, ItemStack heldScroll) {
        ItemStack scroll = heldScroll.clone();
        scroll.setAmount(1);
        p.getInventory().setItemInMainHand(null);

        Holder h = new Holder();
        h.scroll = scroll;
        Inventory inv = Bukkit.createInventory(h, 27, Msg.mm("<aqua>Sealing Scroll"));
        h.inv = inv;

        byte[] data = scroll.getItemMeta().getPersistentDataContainer().get(storeKey, PersistentDataType.BYTE_ARRAY);
        if (data != null && data.length > 0) {
            try {
                for (ItemStack it : ItemStack.deserializeItemsFromBytes(data)) {
                    if (it != null && !it.getType().isAir()) inv.addItem(it);
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Could not read sealed items: " + ex.getMessage());
            }
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        if (!(e.getPlayer() instanceof Player p)) return;

        List<ItemStack> keep = new ArrayList<>();
        for (ItemStack it : h.inv.getContents()) {
            if (it == null || it.getType().isAir()) continue;
            if (plugin.items().isTag(it, "sealing_scroll")) {
                ItemManager.give(p, it); // no nesting
            } else {
                keep.add(it);
            }
        }
        h.inv.clear();

        ItemStack scroll = h.scroll;
        ItemMeta m = scroll.getItemMeta();
        if (keep.isEmpty()) {
            m.getPersistentDataContainer().remove(storeKey);
        } else {
            m.getPersistentDataContainer().set(storeKey, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(keep));
        }
        m.lore(List.of(Msg.item("<gray>Right-click to open and seal items inside."),
                Msg.item("<gray>Sealed stacks: <white>" + keep.size())));
        scroll.setItemMeta(m);
        ItemManager.give(p, scroll);
    }
}
