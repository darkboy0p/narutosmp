package dev.shinobi;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ShinobiCommands implements CommandExecutor, TabCompleter {
    private final ShinobiPlugin plugin;

    public ShinobiCommands(ShinobiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "shinobi" -> shinobi(sender, args);
            case "bind" -> {
                if (!sender.hasPermission("shinobi.admin")) {
                    Msg.send(sender, "<red>No permission.");
                    return true;
                }
                if (args.length < 2) {
                    Msg.send(sender, "<gray>Usage: /bind [player] [uchiha|hyuga|uzumaki|hatake|nara|none]");
                    return true;
                }
                bindClan(sender, args[0], args[1]);
            }
            case "mission" -> mission(sender, args);
            case "jutsu" -> jutsu(sender, args);
            case "bond" -> bond(sender, args);
            case "dojutsu" -> {
                Player p = player(sender);
                if (p == null) return true;
                plugin.dojutsu().toggle(p, plugin.profiles().get(p));
            }
            case "gate" -> {
                Player p = player(sender);
                if (p == null) return true;
                Profile pr = plugin.profiles().get(p);
                if (args.length > 0 && args[0].equalsIgnoreCase("close")) plugin.gates().close(p, pr, null);
                else plugin.gates().open(p, pr);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        Msg.send(s, "<red>Players only.");
        return null;
    }

    // ------------------------------------------------------------------ /shinobi

    private void shinobi(CommandSender sender, String[] args) {
        if (args.length == 0) {
            Player p = player(sender);
            if (p != null) plugin.menu().open(p);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "stats" -> {
                Player p = player(sender);
                if (p == null) return;
                Profile pr = plugin.profiles().get(p);
                Msg.send(p, "<gray>Nature: " + (pr.natureRevealed && pr.nature != null ? pr.nature.colored() : "<white>unknown"));
                Msg.send(p, "<gray>Clan: " + (pr.clan != null && pr.awakened ? pr.clan.colored() : "<white>unknown"));
                Msg.send(p, "<gray>Chakra: <aqua>" + (int) pr.chakra + "<gray>/<aqua>" + (int) plugin.chakra().effectiveMax(pr));
                if (pr.mangekyo != null) Msg.send(p, "<gray>Mangekyou: <dark_red>" + MangekyoManager.display(pr.mangekyo));
            }
            case "weights" -> {
                Player p = player(sender);
                if (p != null) plugin.training().toggleWeights(p, plugin.profiles().get(p));
            }
            case "admin" -> admin(sender, Arrays.copyOfRange(args, 1, args.length));
            default -> {
                Msg.send(sender, "<gray>/shinobi <white>(menu)<gray>, /shinobi stats, /jutsu list, /bond, /dojutsu toggle, /gate open|close");
                Msg.send(sender, "<gray>Press <white>F<gray> for hand seals. Shift+F toggles your dojutsu.");
            }
        }
    }

    // ------------------------------------------------------------------ admin

    private void admin(CommandSender sender, String[] a) {
        if (!sender.hasPermission("shinobi.admin")) {
            Msg.send(sender, "<red>No permission.");
            return;
        }
        if (a.length == 0) {
            Msg.send(sender, "<gray>Admin: clan, nature, learn, forget, scroll, item, paper, chakra, awaken, reset, mangekyo, reload");
            return;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            plugin.reloadConfig();
            Msg.send(sender, "<green>Config reloaded.");
            return;
        }
        if (a.length < 2) {
            Msg.send(sender, "<red>Missing player. Usage: /shinobi admin " + sub + " [player] ...");
            return;
        }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) {
            Msg.send(sender, "<red>Player not online.");
            return;
        }
        Profile pr = plugin.profiles().get(t);
        switch (sub) {
            case "clan" -> {
                if (a.length < 3) {
                    Msg.send(sender, "<red>Usage: /shinobi admin clan [player] [clan|none]");
                    return;
                }
                bindClan(sender, a[1], a[2]);
            }
            case "nature" -> {
                Nature n = a.length > 2 ? Nature.parse(a[2]) : null;
                if (n == null) {
                    Msg.send(sender, "<red>Natures: fire, water, wind, lightning, earth");
                    return;
                }
                pr.nature = n;
                Msg.send(sender, "<green>" + t.getName() + "'s nature is now " + n.display + ".");
            }
            case "learn" -> {
                if (a.length < 3) {
                    Msg.send(sender, "<red>Usage: /shinobi admin learn [player] [jutsu|all]");
                    return;
                }
                if (a[2].equalsIgnoreCase("all")) {
                    for (Jutsu j : plugin.jutsus().all()) if (j.needsKnown) pr.known.add(j.id);
                    Msg.send(sender, "<green>Taught all scroll techniques.");
                } else {
                    Jutsu j = plugin.jutsus().get(a[2]);
                    if (j == null) {
                        Msg.send(sender, "<red>Unknown jutsu. Ids: " + String.join(", ", plugin.jutsus().ids()));
                        return;
                    }
                    pr.known.add(j.id);
                    Msg.send(sender, "<green>Taught " + j.name + ".");
                }
            }
            case "forget" -> {
                if (a.length < 3) {
                    Msg.send(sender, "<red>Usage: /shinobi admin forget [player] [jutsu]");
                    return;
                }
                pr.known.remove(a[2].toLowerCase(Locale.ROOT));
                Msg.send(sender, "<green>Done.");
            }
            case "scroll" -> {
                Jutsu j = a.length > 2 ? plugin.jutsus().get(a[2]) : null;
                if (j == null) {
                    Msg.send(sender, "<red>Unknown jutsu. Ids: " + String.join(", ", plugin.jutsus().ids()));
                    return;
                }
                t.getInventory().addItem(plugin.items().scroll(j));
                Msg.send(sender, "<green>Gave a " + j.name + " scroll to " + t.getName() + ".");
            }
            case "paper" -> {
                int n = 1;
                if (a.length > 2) {
                    try {
                        n = Math.max(1, Math.min(64, Integer.parseInt(a[2])));
                    } catch (NumberFormatException ignored) {
                    }
                }
                t.getInventory().addItem(plugin.items().create(ItemManager.Type.CHAKRA_PAPER, n));
                Msg.send(sender, "<green>Gave chakra paper.");
            }
            case "item" -> {
                ItemManager.Type type = a.length > 2 ? ItemManager.Type.byId(a[2]) : null;
                if (type == null) {
                    StringBuilder ids = new StringBuilder();
                    for (ItemManager.Type x : ItemManager.Type.values()) ids.append(x.id).append(' ');
                    Msg.send(sender, "<red>Usage: /shinobi admin item [player] [item] [amount]. Items: " + ids);
                    return;
                }
                int n = 1;
                if (a.length > 3) {
                    try {
                        n = Math.max(1, Math.min(64, Integer.parseInt(a[3])));
                    } catch (NumberFormatException ignored) {
                    }
                }
                ItemManager.give(t, plugin.items().create(type, n));
                Msg.send(sender, "<green>Gave " + n + "x " + type.id + " to " + t.getName() + ".");
            }
            case "chakra" -> {
                try {
                    double v = Double.parseDouble(a.length > 2 ? a[2] : "");
                    pr.maxChakra = Math.max(10.0, v);
                    pr.chakra = pr.maxChakra;
                    Msg.send(sender, "<green>Max chakra set to " + (int) pr.maxChakra + ".");
                } catch (NumberFormatException ex) {
                    Msg.send(sender, "<red>Usage: /shinobi admin chakra [player] [max]");
                }
            }
            case "awaken" -> {
                if (pr.clan == null) {
                    Msg.send(sender, "<red>That player has no clan. Use /bind first.");
                    return;
                }
                pr.awakened = true;
                Msg.send(sender, "<green>Awakened " + t.getName() + "'s bloodline.");
            }
            case "reset" -> {
                plugin.mangekyo().revoke(pr);
                pr.rolled = false;
                pr.nature = null;
                pr.natureRevealed = false;
                pr.clan = null;
                pr.awakened = false;
                pr.known.clear();
                pr.mastery.clear();
                pr.maxChakra = 100.0;
                pr.strain = 0;
                pr.dojutsuActive = false;
                Msg.send(sender, "<green>Profile reset. They roll again on next join.");
            }
            case "mangekyo" -> {
                String op = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "";
                if (op.equals("revoke")) {
                    plugin.mangekyo().revoke(pr);
                    Msg.send(sender, "<green>Mangekyou revoked.");
                } else if (op.equals("assign") && a.length > 3) {
                    String ability = a[3].toLowerCase(Locale.ROOT);
                    if (pr.mangekyo != null) plugin.mangekyo().revoke(pr);
                    if (plugin.mangekyo().assign(t, pr, ability)) {
                        if (pr.clan == null) pr.clan = Clan.UCHIHA;
                        pr.awakened = true;
                        Msg.send(sender, "<green>Assigned " + MangekyoManager.display(ability) + ".");
                    } else {
                        Msg.send(sender, "<red>Ability unknown or already owned. Free: " + String.join(", ", plugin.mangekyo().freeAbilities()));
                    }
                } else {
                    Msg.send(sender, "<red>Usage: /shinobi admin mangekyo [player] assign [ability] | revoke");
                }
            }
            default -> Msg.send(sender, "<red>Unknown admin subcommand.");
        }
        plugin.profiles().save(pr);
    }

    /** The "bind" action: gives the player a clan and awakens its abilities. */
    private void bindClan(CommandSender sender, String playerName, String clanName) {
        Player t = Bukkit.getPlayerExact(playerName);
        if (t == null) {
            Msg.send(sender, "<red>Player not online.");
            return;
        }
        Profile pr = plugin.profiles().get(t);
        if (clanName.equalsIgnoreCase("none")) {
            plugin.dojutsu().deactivate(t, pr, null);
            plugin.mangekyo().revoke(pr);
            pr.clan = null;
            pr.awakened = false;
            plugin.profiles().save(pr);
            Msg.send(sender, "<green>Removed " + t.getName() + "'s clan.");
            return;
        }
        Clan c = Clan.parse(clanName);
        if (c == null) {
            Msg.send(sender, "<red>Clans: uchiha, hyuga, uzumaki, hatake, nara, none");
            return;
        }
        if (pr.clan != c) {
            plugin.dojutsu().deactivate(t, pr, null);
            if (c != Clan.UCHIHA) plugin.mangekyo().revoke(pr);
        }
        pr.clan = c;
        pr.awakened = true;
        plugin.profiles().save(pr);
        Msg.send(sender, "<green>Bound " + t.getName() + " to " + c.display + " (" + c.abilities + ").");
        Msg.send(t, "<gray>The blood of the <white>" + c.colored() + "<gray> clan awakens within you.");
    }

    // ------------------------------------------------------------------ /jutsu

    private void jutsu(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) return;
        Profile pr = plugin.profiles().get(p);
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            boolean any = false;
            for (Jutsu j : plugin.jutsus().all()) {
                boolean usable = j.needsKnown ? pr.known.contains(j.id) : (j.clan != null && pr.clan == j.clan && pr.awakened);
                if (!usable) continue;
                any = true;
                Msg.send(p, "<white>" + j.name + " <gray>[" + j.id + "] " + j.sealText() + " <dark_gray>(x" + pr.masteryOf(j.id) + ")");
            }
            if (!any) Msg.send(p, "<gray>You know no techniques yet.");
            return;
        }
        if (args[0].equalsIgnoreCase("cast") && args.length > 1) {
            Jutsu j = plugin.jutsus().get(args[1]);
            if (j == null) {
                Msg.send(p, "<red>Unknown jutsu.");
                return;
            }
            if (!j.seals.isEmpty()) {
                Msg.send(p, "<red>That technique needs hand seals: " + j.sealText() + ". Press F.");
                return;
            }
            plugin.casting().cast(p, pr, j);
            return;
        }
        Msg.send(p, "<gray>Usage: /jutsu list | /jutsu cast [id]");
    }

    // ------------------------------------------------------------------ /mission

    private void mission(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) return;
        Profile pr = plugin.profiles().get(p);
        if (args.length > 1 && args[0].equalsIgnoreCase("abandon")) {
            try {
                plugin.missions().abandon(p, pr, Integer.parseInt(args[1]) - 1);
            } catch (NumberFormatException ex) {
                Msg.send(p, "<red>Usage: /mission abandon [number]");
            }
            return;
        }
        if (pr.missions.isEmpty()) {
            Msg.send(p, "<gray>You have no missions. Craft and place a <white>Mission Board<gray> to take some.");
            return;
        }
        int i = 1;
        for (MissionState m : pr.missions) {
            String detail = m.type == MissionType.DELIVERY
                    ? "deliver to X " + m.destX + " Z " + m.destZ
                    : m.type == MissionType.GATHER ? "hand in at the board"
                    : m.progress + "/" + m.required;
            Msg.send(p, "<white>" + i + ". " + MissionManager.diffColor(m.difficulty) + "[" + m.difficulty + "] <white>" + m.title + " <gray>" + detail);
            i++;
        }
    }

    // ------------------------------------------------------------------ /bond

    private void bond(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) return;
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            plugin.bonds().list(p);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "request" -> {
                Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                if (t == null) Msg.send(p, "<red>Player not online.");
                else plugin.bonds().request(p, t);
            }
            case "accept" -> plugin.bonds().accept(p);
            case "break" -> {
                if (args.length > 1) plugin.bonds().breakBond(p, args[1]);
                else Msg.send(p, "<red>Usage: /bond break [player]");
            }
            default -> Msg.send(p, "<gray>Usage: /bond request [player] | accept | break [player] | list");
        }
    }

    // ------------------------------------------------------------------ tab complete

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);

        if (name.equals("bind")) {
            if (args.length == 1) players(out);
            else if (args.length == 2) clans(out);
        } else if (name.equals("jutsu")) {
            if (args.length == 1) out.addAll(List.of("list", "cast"));
            else if (args.length == 2 && args[0].equalsIgnoreCase("cast")) out.addAll(plugin.jutsus().ids());
        } else if (name.equals("bond")) {
            if (args.length == 1) out.addAll(List.of("request", "accept", "break", "list"));
            else if (args.length == 2) players(out);
        } else if (name.equals("mission")) {
            if (args.length == 1) out.addAll(List.of("list", "abandon"));
        } else if (name.equals("gate")) {
            if (args.length == 1) out.addAll(List.of("open", "close"));
        } else if (name.equals("shinobi")) {
            if (args.length == 1) {
                out.addAll(List.of("stats", "help", "weights"));
                if (sender.hasPermission("shinobi.admin")) out.add("admin");
            } else if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("shinobi.admin")) {
                if (args.length == 2) {
                    out.addAll(List.of("clan", "nature", "learn", "forget", "scroll", "item", "paper", "chakra", "awaken", "reset", "mangekyo", "reload"));
                } else if (args.length == 3) {
                    players(out);
                } else if (args.length == 4) {
                    String sub = args[1].toLowerCase(Locale.ROOT);
                    switch (sub) {
                        case "clan" -> clans(out);
                        case "nature" -> Arrays.stream(Nature.values()).forEach(n -> out.add(n.name().toLowerCase(Locale.ROOT)));
                        case "learn" -> {
                            out.add("all");
                            out.addAll(plugin.jutsus().ids());
                        }
                        case "forget", "scroll" -> out.addAll(plugin.jutsus().ids());
                        case "item" -> {
                            for (ItemManager.Type x : ItemManager.Type.values()) out.add(x.id);
                        }
                        case "mangekyo" -> out.addAll(List.of("assign", "revoke"));
                        default -> {
                        }
                    }
                } else if (args.length == 5 && args[1].equalsIgnoreCase("mangekyo")) {
                    out.addAll(MangekyoManager.ABILITIES);
                }
            }
        }
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    private void players(List<String> out) {
        for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
    }

    private void clans(List<String> out) {
        for (Clan c : Clan.values()) out.add(c.name().toLowerCase(Locale.ROOT));
        out.add("none");
    }
}
