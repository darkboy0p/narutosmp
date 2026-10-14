package dev.shinobi;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Shinobi Headband as worn cosmetic gear that works with ANY helmet.
 *
 * <p>It is not armor and never uses the head equipment slot. When a player "wears" it (right-click the item, or
 * /shinobi headband), the item is stored as a profile flag and one {@code ItemDisplay} entity with a custom 3D model
 * ({@code headgear_<style>}: cloth band, steel plate with engraving, knot and, while a dojutsu is active, the eye art) is kept
 * at the player's head position and rotation. Because the model is larger than the vanilla helmet cube, it is drawn over
 * leather, iron, diamond or netherite helmets without replacing them or touching armor stats.
 *
 * <p>Server-side only, visible to every client that has the resource pack. Limits: tracking is a teleport every
 * {@code headgear.update-ticks} (2 by default) with client-side smoothing, so it can lag slightly during fast motion;
 * it is hidden while swimming, gliding or sleeping; and it has not been checked in a client. A client mod could attach it
 * exactly to the head bone; none exists in this project.
 */
public final class HeadgearManager implements Listener {
    private static final class Entry {
        ItemDisplay display;
        String modelKey;           // style key currently shown, or null when hidden
        EffectRegistry.Handle handle;
    }

    private final ShinobiPlugin plugin;
    private final Map<UUID, Entry> shown = new HashMap<>();
    private BukkitTask task;

    public HeadgearManager(ShinobiPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ lifecycle

    private boolean enabled() {
        return plugin.getConfig().getBoolean("headgear.enabled", true) && plugin.getConfig().getBoolean("resource-pack.enabled", false);
    }

    public void start() {
        long every = Math.max(1, Math.min(10, plugin.getConfig().getInt("headgear.update-ticks", 2)));
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, every);
    }

    public int active() {
        return shown.size();
    }

    private void tick() {
        if (!enabled()) {
            if (!shown.isEmpty()) removeAll();
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!plugin.profiles().isLoaded(p.getUniqueId())) continue;
            Profile pr = plugin.profiles().get(p);
            if (pr.headbandWorn) ensure(p, pr);
            else if (shown.containsKey(p.getUniqueId())) remove(p.getUniqueId());
        }
        for (UUID id : new ArrayList<>(shown.keySet())) { // wearers who left without a cleanup event
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) remove(id);
        }
    }

    // ------------------------------------------------------------------ display

    /** The style the player's face should currently show, derived from server-side state only. */
    public String keyFor(Profile pr) {
        BloodlineService bl = plugin.bloodlines();
        boolean active = pr.dojutsuActive && pr.clan != null && pr.clan.hasDojutsu()
                && bl.check(pr, pr.clan == Clan.UCHIHA ? "sharingan" : "byakugan") == BloodlineRules.Result.OK;
        boolean eternal = pr.eternal && bl.check(pr, "eternal_mangekyo") == BloodlineRules.Result.OK;
        boolean rinnegan = bl.check(pr, "rinnegan") == BloodlineRules.Result.OK;
        boolean tenseigan = bl.check(pr, "tenseigan") == BloodlineRules.Result.OK;
        return EyeStyle.key(pr.clan, active, plugin.hud().sharinganStage(pr), pr.mangekyo, eternal, rinnegan, tenseigan);
    }

    private ItemStack modelItem(String styleKey) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.setItemModel(new NamespacedKey("shinobi", HeadgearRules.modelId(styleKey)));
        it.setItemMeta(m);
        return it;
    }

    private Transformation transformation() {
        // Item models face "north"; entities face "south" at yaw 0, so the model is turned by headgear.model-yaw-offset (default 180).
        float yaw = (float) Math.toRadians(plugin.getConfig().getDouble("headgear.model-yaw-offset", 180.0));
        return new Transformation(new Vector3f(), new AxisAngle4f(yaw, 0f, 1f, 0f), new Vector3f(1f, 1f, 1f), new AxisAngle4f());
    }

    private void ensure(Player p, Profile pr) {
        UUID id = p.getUniqueId();
        boolean visible = HeadgearRules.visible(p.isSwimming(), p.isGliding(), p.isSleeping(), p.isDead(),
                p.getGameMode() == org.bukkit.GameMode.SPECTATOR, p.isInvisible());
        String key = visible ? keyFor(pr) : null;
        Entry e = shown.get(id);
        if (e != null && !e.display.isValid()) {   // killed by a world change, chunk unload, /kill @e ...
            remove(id);
            e = null;
        }
        if (e == null) {
            if (key == null) return;
            if (plugin.effects().activeFor(id) >= 24) return; // effect cap (see EffectRegistry): try again next tick
            Entry fresh = new Entry();
            fresh.handle = plugin.effects().begin(id, "headgear", () -> removeEntity(id));
            if (fresh.handle == null) return;
            Location at = p.getEyeLocation();
            fresh.display = p.getWorld().spawn(at, ItemDisplay.class, d -> {
                d.setItemStack(modelItem(key));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setTransformation(transformation());
                d.setTeleportDuration(2);
                d.setBillboard(Display.Billboard.FIXED);
                d.setViewRange(0.5f);
                d.setPersistent(false);
            });
            fresh.modelKey = key;
            shown.put(id, fresh);
            if (!plugin.getConfig().getBoolean("headgear.show-to-owner", false)) p.hideEntity(plugin, fresh.display);
            return;
        }
        if (key == null) {
            if (e.modelKey != null) {
                e.display.setItemStack(new ItemStack(Material.AIR)); // draws nothing while swimming/gliding/sleeping
                e.modelKey = null;
            }
        } else if (!key.equals(e.modelKey)) {
            e.display.setItemStack(modelItem(key));
            e.modelKey = key;
        }
        e.display.teleport(p.getEyeLocation());
    }

    /** Immediate update (dojutsu toggled, stage changed). */
    public void refresh(Player p) {
        if (!enabled() || !plugin.profiles().isLoaded(p.getUniqueId())) return;
        Profile pr = plugin.profiles().get(p);
        if (pr.headbandWorn) ensure(p, pr);
    }

    private void removeEntity(UUID id) {
        Entry e = shown.remove(id);
        if (e != null && e.display != null && e.display.isValid()) e.display.remove();
    }

    private void remove(UUID id) {
        Entry e = shown.get(id);
        if (e == null) return;
        if (e.handle != null) e.handle.end(); // runs removeEntity through the registry
        removeEntity(id);
    }

    public void removeAll() {
        for (UUID id : new ArrayList<>(shown.keySet())) removeEntity(id);
    }

    // ------------------------------------------------------------------ wearing / removing

    /** Wear one headband from the given stack. */
    public boolean equip(Player p, ItemStack stack) {
        Profile pr = plugin.profiles().get(p);
        if (pr.headbandWorn) {
            Msg.bar(p, "<gray>You are already wearing your headband. Use <white>/shinobi headband<gray> to take it off.");
            return false;
        }
        stack.setAmount(stack.getAmount() - 1);
        pr.headbandWorn = true;
        plugin.profiles().save(pr);
        Msg.bar(p, "<blue>You tie on your Shinobi Headband.");
        refresh(p);
        return true;
    }

    /** /shinobi headband: take it off if worn, otherwise wear one from the inventory. */
    public void toggle(Player p) {
        Profile pr = plugin.profiles().get(p);
        if (pr.headbandWorn) {
            pr.headbandWorn = false;
            plugin.profiles().save(pr);
            remove(p.getUniqueId());
            ItemStack back = plugin.items().create(ItemManager.Type.HEADBAND, 1);
            for (ItemStack left : p.getInventory().addItem(back).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
            Msg.bar(p, "<gray>You take off your Shinobi Headband.");
            return;
        }
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && plugin.items().isTag(it, "headband")) {
                equip(p, it);
                return;
            }
        }
        Msg.bar(p, "<red>You do not have a Shinobi Headband. Craft one: leather + iron ingot + blue dye.");
    }

    // ------------------------------------------------------------------ legacy upgrade

    /** Headbands from older versions were leather helmets. Convert them: inventory copies become the new item, a worn one becomes worn gear. */
    private void upgradeLegacy(Player p) {
        Profile pr = plugin.profiles().get(p);
        ItemStack helmet = p.getInventory().getHelmet();
        if (helmet != null && helmet.getType() == Material.LEATHER_HELMET && plugin.items().isTag(helmet, "headband")) {
            p.getInventory().setHelmet(null);
            pr.headbandWorn = true;
            Msg.send(p, "<blue>Your Shinobi Headband is now cosmetic gear and no longer takes your helmet slot.");
        }
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it != null && it.getType() == Material.LEATHER_HELMET && plugin.items().isTag(it, "headband")) {
                p.getInventory().setItem(i, plugin.items().create(ItemManager.Type.HEADBAND, it.getAmount()));
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) upgradeLegacy(p);
        }, 20L);
    }
}
