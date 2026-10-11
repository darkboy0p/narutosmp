package dev.shinobi;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BondManager {
    private final ShinobiPlugin plugin;
    private final Map<UUID, UUID> pending = new HashMap<>();      // target -> requester
    private final Map<UUID, Long> pendingExpiry = new HashMap<>();

    public BondManager(ShinobiPlugin plugin) {
        this.plugin = plugin;
    }

    public void request(Player from, Player to) {
        if (from.equals(to)) {
            Msg.send(from, "<red>You can't bond with yourself.");
            return;
        }
        Profile fp = plugin.profiles().get(from);
        if (fp.bonds.contains(to.getUniqueId())) {
            Msg.send(from, "<red>You are already bonded with " + to.getName() + ".");
            return;
        }
        pending.put(to.getUniqueId(), from.getUniqueId());
        pendingExpiry.put(to.getUniqueId(), System.currentTimeMillis() + 60_000L);
        Msg.send(from, "<gray>Bond request sent to <white>" + to.getName() + "<gray>.");
        Msg.send(to, "<white>" + from.getName() + " <gray>wants to bond with you. Type <yellow>/bond accept<gray> within 60 seconds.");
    }

    public void accept(Player p) {
        UUID req = pending.remove(p.getUniqueId());
        Long exp = pendingExpiry.remove(p.getUniqueId());
        if (req == null || exp == null || exp < System.currentTimeMillis()) {
            Msg.send(p, "<red>You have no pending bond request.");
            return;
        }
        Profile a = plugin.profiles().get(p);
        Profile b = plugin.profiles().get(req);
        a.bonds.add(req);
        b.bonds.add(p.getUniqueId());
        plugin.profiles().save(a);
        plugin.profiles().save(b);
        Msg.send(p, "<green>You are now bonded.");
        Player other = Bukkit.getPlayer(req);
        if (other != null) Msg.send(other, "<green>" + p.getName() + " accepted. You are now bonded.");
    }

    public void breakBond(Player p, String name) {
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        UUID id = off.getUniqueId();
        Profile a = plugin.profiles().get(p);
        if (!a.bonds.remove(id)) {
            Msg.send(p, "<red>You are not bonded with " + name + ".");
            return;
        }
        Profile b = plugin.profiles().get(id);
        b.bonds.remove(p.getUniqueId());
        plugin.profiles().save(a);
        plugin.profiles().save(b);
        Msg.send(p, "<gray>Bond with <white>" + name + "<gray> broken.");
    }

    public void list(Player p) {
        Profile a = plugin.profiles().get(p);
        if (a.bonds.isEmpty()) {
            Msg.send(p, "<gray>You have no bonds.");
            return;
        }
        StringBuilder sb = new StringBuilder("<gray>Bonds: <white>");
        boolean first = true;
        for (UUID u : a.bonds) {
            String n = Bukkit.getOfflinePlayer(u).getName();
            if (!first) sb.append(", ");
            sb.append(n == null ? u.toString().substring(0, 8) : n);
            first = false;
        }
        Msg.send(p, sb.toString());
    }

    /** A player died: any bonded awakened Uchiha nearby may awaken the Mangekyou. */
    public void onDeath(Player victim) {
        Profile vp = plugin.profiles().get(victim);
        double range = plugin.getConfig().getDouble("bond.mangekyo-witness-range", 40.0);
        for (UUID u : vp.bonds) {
            Player holder = Bukkit.getPlayer(u);
            if (holder == null || !holder.getWorld().equals(victim.getWorld())) continue;
            if (holder.getLocation().distance(victim.getLocation()) > range) continue;
            plugin.mangekyo().tryAwaken(holder, victim.getName());
        }
    }
}
