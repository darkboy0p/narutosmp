package dev.shinobi;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.List;

/**
 * Hand-seal input. Swap-hands (F) toggles seal mode; Shift+F toggles your dojutsu.
 * In seal mode you are parked on hotbar slot 9: keys 1-8 form seals 1-8, Shift+1-4 form seals 9-12.
 */
public final class SealManager implements Listener {
    private final ShinobiPlugin plugin;

    public SealManager(ShinobiPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (!plugin.getConfig().getBoolean("seal-mode.enabled", true)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        Profile pr = plugin.profiles().get(p);
        if (p.isSneaking()) {
            plugin.dojutsu().toggle(p, pr);
        } else if (pr.session != null) {
            exit(p, pr);
        } else {
            enter(p, pr);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        Profile pr = plugin.profiles().get(p);
        if (pr.session == null) return;
        e.setCancelled(true);
        Seal s = Seal.fromInput(e.getNewSlot(), p.isSneaking());
        if (s != null) input(p, pr, s);
    }

    public void enter(Player p, Profile pr) {
        pr.session = new SealSession(p.getInventory().getHeldItemSlot());
        p.getInventory().setHeldItemSlot(8);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.5f);
        showSequence(p, pr);
    }

    public void exit(Player p, Profile pr) {
        if (pr.session == null) return;
        int back = pr.session.originalSlot;
        pr.session = null;
        p.getInventory().setHeldItemSlot(back);
    }

    public void showSequence(Player p, Profile pr) {
        if (pr.session == null) return;
        List<Seal> seq = pr.session.seq;
        StringBuilder sb = new StringBuilder("<yellow>Seals: <white>");
        if (seq.isEmpty()) {
            sb.append("<gray>(keys 1-8, Shift+1-4)  F to cancel");
        } else {
            boolean glyphs = plugin.hud().on();
            for (int i = 0; i < seq.size(); i++) {
                if (i > 0) sb.append(glyphs ? " " : " <gray>> <white>");
                sb.append(glyphs ? plugin.hud().seal(seq.get(i)) : seq.get(i).display);
            }
        }
        Msg.bar(p, sb.toString());
    }

    private void input(Player p, Profile pr, Seal seal) {
        long now = System.currentTimeMillis();
        SealSession s = pr.session;
        long timeout = plugin.getConfig().getLong("seal-mode.seal-timeout-ms", 3000L);
        if (!s.seq.isEmpty() && now - s.last > timeout) s.seq.clear();
        s.seq.add(seal);
        s.last = now;
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f + 0.05f * seal.ordinal());

        Jutsu exact = plugin.jutsus().matchExact(s.seq);
        if (exact != null) {
            exit(p, pr);
            plugin.casting().cast(p, pr, exact);
            return;
        }
        if (!plugin.jutsus().hasPrefix(s.seq)) {
            s.seq.clear();
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
            Msg.bar(p, "<red>Seals broken. Start again.");
            return;
        }
        showSequence(p, pr);
    }
}
