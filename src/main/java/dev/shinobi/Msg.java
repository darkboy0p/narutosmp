package dev.shinobi;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class Msg {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String PREFIX = "<dark_gray>[<red>Shinobi<dark_gray>] <gray>";

    private Msg() {}

    public static Component mm(String s) {
        return MM.deserialize(s);
    }

    /** For item names/lore: removes the default italics. */
    public static Component item(String s) {
        return MM.deserialize("<!italic>" + s);
    }

    public static void send(CommandSender to, String s) {
        to.sendMessage(mm(PREFIX + s));
    }

    public static void bar(Player p, String s) {
        p.sendActionBar(mm(s));
    }
}
