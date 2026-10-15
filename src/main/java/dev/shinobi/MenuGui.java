package dev.shinobi;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Read-only hub menu: profile, jutsu book, bonds, plus nature / dojutsu / clan icons (animated with the pack). */
public final class MenuGui implements Listener {
    private static final class Holder implements InventoryHolder {
        private Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final ShinobiPlugin plugin;

    public MenuGui(ShinobiPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        Profile pr = plugin.profiles().get(p);
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 27, Msg.mm("<dark_red>Shinobi"));
        h.inv = inv;

        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> ", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);

        // Profile
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Nature: " + (pr.natureRevealed && pr.nature != null ? pr.nature.colored() : "<white>??? <dark_gray>(use chakra paper)"));
        if (pr.clan != null && pr.awakened) {
            lore.add("<gray>Clan: " + pr.clan.colored());
            lore.add("<gray>Abilities: <white>" + pr.clan.abilities);
        } else {
            lore.add("<gray>Clan: <white>???");
        }
        double max = plugin.chakra().effectiveMax(pr);
        lore.add("<gray>Chakra: <aqua>" + (int) pr.chakra + "<gray>/<aqua>" + (int) max);
        if (pr.mangekyo != null) lore.add("<gray>Mangekyou: <dark_red>" + MangekyoManager.display(pr.mangekyo));
        if (pr.strain >= 1) lore.add("<gray>Eye strain: <dark_purple>" + (int) pr.strain + "%");
        lore.add("<gray>Meditate (sneak, stand still) to grow your chakra.");
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(p);
        sm.displayName(Msg.item("<white>" + p.getName()));
        sm.lore(toLore(lore));
        head.setItemMeta(sm);
        inv.setItem(11, head);

        // Jutsu book
        List<String> jl = new ArrayList<>();
        for (Jutsu j : plugin.jutsus().all()) {
            if (!plugin.casting().usable(pr, j)) continue;
            jl.add("<white>" + j.name);
            jl.add("<dark_gray>  " + j.sealText() + "  <gray>x" + pr.masteryOf(j.id));
        }
        if (jl.isEmpty()) jl.add("<gray>You know no techniques yet. Find scrolls!");
        else if (plugin.casting().easy("quick-cast")) jl.add("<green>Click to open and cast");
        inv.setItem(13, named(Material.BOOK, "<gold>Jutsu Book", jl));

        // Bonds
        List<String> bl = new ArrayList<>();
        for (UUID u : pr.bonds) {
            OfflinePlayer o = Bukkit.getOfflinePlayer(u);
            bl.add("<white>" + (o.getName() == null ? u.toString().substring(0, 8) : o.getName()));
        }
        if (bl.isEmpty()) bl.add("<gray>No bonds. Use /bond request [player]");
        inv.setItem(15, named(Material.LEAD, "<red>Bonds", bl));

        // Nature icon (animated with the pack)
        if (pr.natureRevealed && pr.nature != null) {
            ItemStack nat = named(Material.PAPER, pr.nature.colored() + " <gray>Nature",
                    List.of("<gray>Your chakra nature.", "<dark_gray>Strong against: " + strongAgainst(pr.nature)));
            inv.setItem(20, plugin.hud().model(nat, "nature_" + pr.nature.name().toLowerCase(Locale.ROOT)));
        }

        // Dojutsu eye (animated with the pack)
        List<String> el = new ArrayList<>();
        if (pr.clan != null && pr.awakened && pr.clan.hasDojutsu()) {
            el.add("<gray>" + plugin.hud().eyeName(pr));
            el.add(pr.dojutsuActive ? "<green>Active <dark_gray>(Shift+F to close)" : "<gray>Inactive <dark_gray>(Shift+F to open)");
        } else {
            el.add("<gray>You have not awakened a dojutsu.");
        }
        ItemStack eye = named(Material.ENDER_EYE, "<red>Dojutsu", el);
        inv.setItem(22, plugin.hud().model(eye, plugin.hud().eyeKey(pr)));

        // Clan crest
        if (pr.clan != null && pr.awakened) {
            ItemStack crest = named(Material.WHITE_BANNER, pr.clan.colored() + " <gray>Clan",
                    List.of("<gray>" + pr.clan.abilities));
            inv.setItem(24, plugin.hud().model(crest, "clan_" + pr.clan.name().toLowerCase(Locale.ROOT)));
        }

        p.openInventory(inv);
    }

    private static String strongAgainst(Nature n) {
        return switch (n) {
            case FIRE -> "Wind";
            case WIND -> "Lightning";
            case LIGHTNING -> "Earth";
            case EARTH -> "Water";
            case WATER -> "Fire";
        };
    }

    private static ItemStack named(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Msg.item(name));
        if (!lore.isEmpty()) meta.lore(toLore(lore));
        it.setItemMeta(meta);
        return it;
    }

    private static List<Component> toLore(List<String> lines) {
        List<Component> out = new ArrayList<>();
        for (String s : lines) out.add(Msg.item(s));
        return out;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory() || e.getSlot() != 13) return;
        if (!(e.getWhoClicked() instanceof Player p) || !plugin.casting().easy("quick-cast")) return;
        Bukkit.getScheduler().runTask(plugin, () -> plugin.bookGui().open(p));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
