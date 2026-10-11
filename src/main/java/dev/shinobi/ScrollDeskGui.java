package dev.shinobi;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
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

/**
 * Scroll Desk: write a scroll for a jutsu you know and have practiced.
 * Costs one blank scroll, one ink sac and 15 chakra.
 */
public final class ScrollDeskGui implements Listener {
    public static final int CHAKRA_COST = 15;
    public static final int MASTERY_NEEDED = 3;
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private static final class Holder implements InventoryHolder {
        private Inventory inv;
        private final Map<Integer, String> slots = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final ShinobiPlugin plugin;

    public ScrollDeskGui(ShinobiPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        Profile pr = plugin.profiles().get(p);
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 36, Msg.mm("<dark_red>Scroll Desk"));
        h.inv = inv;

        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        pm.displayName(Msg.item("<dark_gray> "));
        pane.setItemMeta(pm);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);

        int n = 0;
        for (Jutsu j : plugin.jutsus().all()) {
            if (!j.needsKnown || !pr.known.contains(j.id) || n >= SLOTS.length) continue;
            boolean ready = pr.masteryOf(j.id) >= MASTERY_NEEDED;
            ItemStack it = new ItemStack(ready ? Material.PAPER : Material.GRAY_DYE);
            ItemMeta m = it.getItemMeta();
            m.displayName(Msg.item((ready ? "<gold>" : "<gray>") + j.name));
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Seals: <white>" + j.sealText());
            lore.add("<gray>Practice: <white>" + Math.min(pr.masteryOf(j.id), MASTERY_NEEDED) + "<gray>/<white>" + MASTERY_NEEDED + " <dark_gray>casts");
            lore.add("<gray>Needs: blank scroll, ink sac, " + CHAKRA_COST + " chakra");
            lore.add(ready ? "<green>Click to write a scroll" : "<red>You cannot teach what you have not practiced.");
            m.lore(lore.stream().map(Msg::item).toList());
            it.setItemMeta(m);
            if (ready) plugin.hud().model(it, plugin.hud().scrollModel(j.id));
            inv.setItem(SLOTS[n], it);
            h.slots.put(SLOTS[n], j.id);
            n++;
        }
        if (n == 0) {
            ItemStack none = new ItemStack(Material.BARRIER);
            ItemMeta m = none.getItemMeta();
            m.displayName(Msg.item("<gray>You know no scroll techniques yet."));
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

        Profile pr = plugin.profiles().get(p);
        Jutsu j = plugin.jutsus().get(id);
        if (j == null || !pr.known.contains(id)) return;
        if (pr.masteryOf(id) < MASTERY_NEEDED) {
            Msg.send(p, "<red>Practice " + j.name + " " + MASTERY_NEEDED + " times before you write it down.");
            return;
        }
        if (pr.chakra < CHAKRA_COST) {
            Msg.send(p, "<red>You need " + CHAKRA_COST + " chakra to infuse the scroll.");
            return;
        }
        boolean hasBlank = plugin.items().has(p.getInventory(), it -> plugin.items().isTag(it, "blank_scroll"));
        boolean hasInk = p.getInventory().contains(Material.INK_SAC);
        if (!hasBlank || !hasInk) {
            Msg.send(p, "<red>You need a Blank Scroll and an ink sac.");
            return;
        }
        plugin.items().removeOne(p.getInventory(), it -> plugin.items().isTag(it, "blank_scroll"));
        plugin.items().removeOne(p.getInventory(), it -> it.getType() == Material.INK_SAC);
        pr.chakra -= CHAKRA_COST;
        ItemManager.give(p, plugin.items().scroll(j));
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
        Msg.send(p, "<green>You wrote a scroll of <white>" + j.name + "<green>.");
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
