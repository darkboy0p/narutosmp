package dev.shinobi;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
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

/** Mission board: today's six missions on the top row, your accepted missions below. */
public final class MissionBoardGui implements Listener {
    private static final int[] BOARD_SLOTS = {10, 11, 12, 14, 15, 16};
    private static final int[] ACTIVE_SLOTS = {29, 31, 33};

    private static final class Holder implements InventoryHolder {
        private Inventory inv;
        private Location boardLoc;
        private final Map<Integer, MissionManager.Template> board = new HashMap<>();
        private final Map<Integer, Integer> active = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final ShinobiPlugin plugin;

    public MissionBoardGui(ShinobiPlugin plugin) {
        this.plugin = plugin;
    }

    private static Material icon(MissionType t) {
        return switch (t) {
            case HUNT -> Material.IRON_SWORD;
            case GATHER -> Material.WHEAT;
            case DELIVERY -> Material.PAPER;
            case CAST -> Material.BLAZE_POWDER;
            case MEDITATE -> Material.AMETHYST_SHARD;
            case TREE_CLIMB -> Material.OAK_LOG;
            case WATER_WALK -> Material.PRISMARINE_SHARD;
            case WATERFALL -> Material.WATER_BUCKET;
            case DUMMY -> Material.HAY_BLOCK;
            case WEIGHTS -> Material.ANVIL;
        };
    }

    private ItemStack mi(MissionType type, String name, List<String> lore) {
        return plugin.hud().model(item(icon(type), name, lore), "mission_" + type.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static ItemStack item(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Msg.item(name));
        meta.lore(lore.stream().map(Msg::item).toList());
        it.setItemMeta(meta);
        return it;
    }

    public void open(Player p, Location boardLoc) {
        Profile pr = plugin.profiles().get(p);
        Holder h = new Holder();
        h.boardLoc = boardLoc;
        Inventory inv = Bukkit.createInventory(h, 45, Msg.mm("<dark_red>Mission Board"));
        h.inv = inv;

        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
        inv.setItem(4, item(Material.WRITABLE_BOOK, "<gold>Today's Missions",
                List.of("<gray>Hold up to 3 missions at once.", "<gray>New missions every day.")));
        inv.setItem(22, item(Material.COMPASS, "<gold>Your Missions",
                List.of("<gray>Left-click: view / hand in", "<gray>Right-click: abandon")));

        List<MissionManager.Template> board = plugin.missions().board();
        for (int i = 0; i < board.size() && i < BOARD_SLOTS.length; i++) {
            MissionManager.Template t = board.get(i);
            String id = MissionManager.missionId(t);
            boolean done = pr.completedMissions.contains(id);
            boolean taken = pr.missions.stream().anyMatch(m -> m.id.equals(id));
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + MissionManager.describe(t.type(), t.target(), t.required()));
            lore.add("<gold>Reward: " + t.reward() + " ryo");
            lore.add(done ? "<green>Completed today" : taken ? "<yellow>Accepted" : "<white>Click to accept");
            inv.setItem(BOARD_SLOTS[i], mi(t.type(),
                    MissionManager.diffColor(t.difficulty()) + "[" + t.difficulty() + "] <white>" + t.title(), lore));
            h.board.put(BOARD_SLOTS[i], t);
        }

        for (int i = 0; i < pr.missions.size() && i < ACTIVE_SLOTS.length; i++) {
            MissionState m = pr.missions.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + MissionManager.describe(m.type, m.target, m.required));
            if (m.type == MissionType.DELIVERY) {
                lore.add("<gray>Destination: X <white>" + m.destX + " <gray>Z <white>" + m.destZ);
            } else if (m.type == MissionType.GATHER) {
                lore.add("<gray>Click to hand in the items.");
            } else {
                lore.add("<gray>Progress: <white>" + m.progress + "<gray>/<white>" + m.required);
            }
            lore.add("<red>Right-click to abandon");
            inv.setItem(ACTIVE_SLOTS[i], mi(m.type,
                    MissionManager.diffColor(m.difficulty) + "[" + m.difficulty + "] <white>" + m.title, lore));
            h.active.put(ACTIVE_SLOTS[i], i);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        Profile pr = plugin.profiles().get(p);
        int slot = e.getSlot();

        MissionManager.Template t = h.board.get(slot);
        if (t != null) {
            String err = plugin.missions().accept(p, pr, t, h.boardLoc);
            if (err != null) {
                Msg.send(p, "<red>" + err);
            } else {
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                Msg.send(p, "<green>Mission accepted: <white>" + t.title());
            }
            open(p, h.boardLoc);
            return;
        }

        Integer idx = h.active.get(slot);
        if (idx != null && idx < pr.missions.size()) {
            if (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT) {
                plugin.missions().abandon(p, pr, idx);
            } else {
                MissionState m = pr.missions.get(idx);
                if (m.type == MissionType.GATHER) plugin.missions().turnIn(p, pr, m);
            }
            open(p, h.boardLoc);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
