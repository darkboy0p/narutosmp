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
            case "clan" -> clanInfo(sender);
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
                Msg.send(sender, "<gray>Press <white>F<gray> then two number keys (1-8) for hand seals; or <white>/jutsu<gray> and click a technique.");
                Msg.send(sender, "<gray>Keybind: <white>/jutsu bind [slot 1-9] [jutsu id]<gray>, then hold <white>Shift + that number key<gray> to cast it.");
                Msg.send(sender, "<gray>Dojutsu: <white>Shift+F<gray> toggles Sharingan (Uchiha) or Byakugan (Hyuga). Costs chakra per second.");
                Msg.send(sender, "<gray>Uchiha: dodge hits, copy jutsu, cheaper fire, Mangekyou. Hyuga: see through walls + Gentle Fist (bare-hand hit seals chakra). Uzumaki: bigger chakra + Chains. Hatake: White Light Sabre. Nara: Shadow Possession.");
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
            Msg.send(sender, "<gray>Admin: clan, nature, learn, forget, scroll, item, paper, chakra, awaken, level, cooldown, refill, mastery, roll, info, eternal, reset, mangekyo, reload");
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
                // /shinobi admin awaken [player] [clan|off]  - override: no near-death needed, can also set the clan.
                if (a.length > 2 && a[2].equalsIgnoreCase("off")) {
                    plugin.dojutsu().deactivate(t, pr, null);
                    pr.awakened = false;
                    Msg.send(sender, "<green>" + t.getName() + "'s bloodline is dormant again.");
                } else if (a.length > 2) {
                    bindClan(sender, a[1], a[2]);
                } else if (pr.clan == null) {
                    Msg.send(sender, "<red>That player has no clan. Use /shinobi admin awaken [player] [clan].");
                    return;
                } else {
                    pr.awakened = true;
                    Msg.send(sender, "<green>Awakened " + t.getName() + "'s bloodline.");
                }
            }
            case "tomoe", "level" -> {
                // /shinobi admin level [player] [1|2|3|auto]  - works for every clan (Uchiha: tomoe count)
                if (a.length < 3) {
                    Msg.send(sender, "<red>Usage: /shinobi admin level [player] [1|2|3|auto]");
                    return;
                }
                if (pr.clan == null) {
                    Msg.send(sender, "<red>That player has no clan. Use /shinobi admin awaken [player] [clan] first.");
                    return;
                }
                if (a[2].equalsIgnoreCase("auto")) {
                    pr.tomoe = 0;
                } else {
                    try {
                        pr.tomoe = Math.max(1, Math.min(3, Integer.parseInt(a[2])));
                    } catch (NumberFormatException ex) {
                        Msg.send(sender, "<red>Usage: /shinobi admin level [player] [1|2|3|auto]");
                        return;
                    }
                    pr.awakened = true;
                }
                Msg.send(sender, "<green>" + t.getName() + "'s " + pr.clan.display + " level: "
                        + (pr.tomoe == 0 ? "automatic (from training)" : pr.tomoe) + ".");
            }
            case "cooldown" -> {
                pr.cooldowns.clear();
                pr.sealedUntil = 0;
                Msg.send(sender, "<green>Cleared " + t.getName() + "'s cooldowns.");
            }
            case "refill" -> {
                pr.chakra = plugin.chakra().effectiveMax(pr);
                pr.strain = 0;
                Msg.send(sender, "<green>Refilled " + t.getName() + "'s chakra.");
            }
            case "mastery" -> {
                // /shinobi admin mastery [player] [jutsu|all] [casts]
                if (a.length < 4) {
                    Msg.send(sender, "<red>Usage: /shinobi admin mastery [player] [jutsu|all] [casts]");
                    return;
                }
                int n;
                try {
                    n = Math.max(0, Math.min(9999, Integer.parseInt(a[3])));
                } catch (NumberFormatException ex) {
                    Msg.send(sender, "<red>Casts must be a number.");
                    return;
                }
                if (a[2].equalsIgnoreCase("all")) {
                    for (Jutsu j : plugin.jutsus().all()) pr.mastery.put(j.id, n);
                    Msg.send(sender, "<green>Set every technique to " + n + " casts for " + t.getName() + ".");
                } else {
                    Jutsu j = plugin.jutsus().get(a[2]);
                    if (j == null) {
                        Msg.send(sender, "<red>Unknown jutsu. Ids: " + String.join(", ", plugin.jutsus().ids()));
                        return;
                    }
                    pr.mastery.put(j.id, n);
                    Msg.send(sender, "<green>" + j.name + " set to " + n + " casts for " + t.getName() + ".");
                }
            }
            case "roll" -> {
                // Re-roll the natural clan chance (same odds as a first join).
                plugin.dojutsu().deactivate(t, pr, null);
                plugin.mangekyo().revoke(pr);
                pr.clan = null;
                pr.awakened = false;
                pr.tomoe = 0;
                List<Clan> order = new ArrayList<>(List.of(Clan.values()));
                java.util.Collections.shuffle(order);
                for (Clan c : order) {
                    double chance = plugin.getConfig().getDouble("clans.roll-chance." + c.name().toLowerCase(Locale.ROOT), c.defaultChance);
                    if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < chance) {
                        pr.clan = c;
                        break;
                    }
                }
                Msg.send(sender, pr.clan == null ? "<gray>" + t.getName() + " rolled no clan."
                        : "<green>" + t.getName() + " rolled " + pr.clan.display + " (dormant until awakened).");
            }
            case "info" -> {
                int casts = 0;
                for (int v : pr.mastery.values()) casts += v;
                Msg.send(sender, "<gray>" + t.getName() + ": nature <white>" + (pr.nature == null ? "none" : pr.nature.display)
                        + "<gray>, clan <white>" + (pr.clan == null ? "none" : pr.clan.display + (pr.awakened ? " (awakened)" : " (dormant)"))
                        + "<gray>, level <white>" + plugin.casting().clanLevel(pr) + (pr.tomoe > 0 ? " (admin)" : "")
                        + "<gray>, chakra <aqua>" + (int) pr.chakra + "/" + (int) plugin.chakra().effectiveMax(pr)
                        + "<gray>, casts <white>" + casts + "<gray>, scrolls learned <white>" + pr.known.size()
                        + (pr.mangekyo != null ? "<gray>, Mangekyou <dark_red>" + MangekyoManager.display(pr.mangekyo) + (pr.eternal ? " (eternal)" : "") : ""));
            }
            case "eternal" -> {
                boolean on = a.length < 3 || !a[2].equalsIgnoreCase("off");
                if (on && pr.mangekyo == null) {
                    Msg.send(sender, "<red>Assign a Mangekyou first: /shinobi admin mangekyo [player] assign [ability]");
                    return;
                }
                pr.eternal = on;
                Msg.send(sender, "<green>Eternal Mangekyou " + (on ? "granted to " : "removed from ") + t.getName() + ".");
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
        if (args.length == 0 && plugin.casting().easy("quick-cast")) {
            plugin.bookGui().open(p);
            return;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            boolean any = false;
            for (Jutsu j : plugin.jutsus().all()) {
                if (!plugin.casting().usable(pr, j)) continue;
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
            if (!j.seals.isEmpty() && !plugin.casting().easy("quick-cast")) {
                Msg.send(p, "<red>That technique needs hand seals: " + j.sealText() + ". Press F.");
                return;
            }
            plugin.casting().cast(p, pr, j);
            return;
        }
        if (args[0].equalsIgnoreCase("info") && args.length > 1) {
            Jutsu j = plugin.jutsus().get(args[1]);
            if (j == null) {
                Msg.send(p, "<red>Unknown jutsu. Ids: " + String.join(", ", plugin.jutsus().ids()));
                return;
            }
            Msg.send(p, "<gold>" + j.name + " <gray>[" + j.id + "]");
            Msg.send(p, "<gray>Seals: <white>" + j.sealText() + " <gray>Chakra: <aqua>" + (int) Math.ceil(plugin.casting().costFor(pr, j))
                    + " <gray>Cooldown: <white>" + (j.cooldownMs / 1000) + "s");
            if (j.nature != null) Msg.send(p, "<gray>Nature: " + j.nature.colored() + (plugin.casting().easy("ignore-nature") ? " <dark_gray>(not required in easy mode)" : ""));
            if (j.clan != null) Msg.send(p, "<gray>Clan: " + j.clan.colored() + " <dark_gray>(awakened)");
            if (j.needsKnown) {
                Msg.send(p, "<gray>Learn: find the scroll in loot, write it at a Scroll Desk" + (plugin.getConfig().getBoolean("scrolls.craftable", true)
                        ? ", or craft it: Blank Scroll + Ink Sac + 2x " + plugin.items().scrollCatalyst(j).name().toLowerCase(Locale.ROOT).replace('_', ' ') : "") + ".");
            }
            return;
        }
        if (args[0].equalsIgnoreCase("bind") && args.length > 2) {
            int slot = slotOf(args[1]);
            Jutsu j = plugin.jutsus().get(args[2]);
            if (slot < 0 || j == null) {
                Msg.send(p, "<red>Usage: /jutsu bind [slot 1-9] [jutsu id]  (see /jutsu list for ids)");
                return;
            }
            pr.binds[slot] = j.id;
            plugin.profiles().save(pr);
            Msg.send(p, "<green>Bound <white>" + j.name + "<green> to slot " + (slot + 1) + ". Hold <white>Shift + " + (slot + 1) + "<green> to cast it.");
            return;
        }
        if (args[0].equalsIgnoreCase("unbind") && args.length > 1) {
            int slot = slotOf(args[1]);
            if (slot < 0) {
                Msg.send(p, "<red>Usage: /jutsu unbind [slot 1-9]");
                return;
            }
            pr.binds[slot] = null;
            plugin.profiles().save(pr);
            Msg.send(p, "<green>Slot " + (slot + 1) + " is unbound.");
            return;
        }
        if (args[0].equalsIgnoreCase("binds")) {
            boolean any = false;
            for (int i = 0; i < 9; i++) {
                Jutsu j = pr.binds[i] == null ? null : plugin.jutsus().get(pr.binds[i]);
                if (j == null) continue;
                any = true;
                Msg.send(p, "<white>Shift+" + (i + 1) + " <gray>= <white>" + j.name);
            }
            if (!any) Msg.send(p, "<gray>No keybinds. /jutsu bind [slot 1-9] [jutsu id]");
            return;
        }
        Msg.send(p, "<gray>Usage: /jutsu (book) | list | info [id] | cast [id] | bind [slot] [id] | unbind [slot] | binds");
    }

    private void clanInfo(CommandSender sender) {
        Player p = player(sender);
        if (p == null) return;
        Profile pr = plugin.profiles().get(p);
        Clan c = pr.clan;
        if (c == null) {
            Msg.send(p, "<gray>You carry no clan blood. A few players are born into one when they first join; an admin can also bind one.");
            return;
        }
        if (!pr.awakened) {
            double hearts = plugin.getConfig().getDouble("awakening.near-death-health", 4.0) / 2.0;
            Msg.send(p, "<gray>Your " + c.colored() + "<gray> blood is dormant. Survive at <white>" + (hearts == (int) hearts ? String.valueOf((int) hearts) : String.valueOf(hearts))
                    + "<gray> hearts or less to awaken it.");
            Msg.send(p, "<gray>Waiting inside you: <white>" + c.abilities);
            return;
        }
        int lv = plugin.casting().clanLevel(pr);
        int total = 0;
        for (int v : pr.mastery.values()) total += v;
        Msg.send(p, c.colored() + " <gray>clan - level <white>" + lv + "<gray>/3" + (c == Clan.UCHIHA && pr.mangekyo == null ? " (" + lv + " tomoe)" : ""));
        Msg.send(p, "<gray>Abilities: <white>" + c.abilities);
        Msg.send(p, "<gray>Perks now: <white>" + clanPerks(c, lv));
        if (pr.tomoe > 0) Msg.send(p, "<gray>Your level was set by an admin.");
        else if (lv < 3) Msg.send(p, "<gray>Next level after <white>" + Math.max(0, (lv == 1 ? 25 : 75) - total) + "<gray> more technique casts.");
        else Msg.send(p, "<green>Max level reached.");
        if (c.hasDojutsu()) Msg.send(p, "<gray>Dojutsu: <white>Shift+F<gray> or <white>/dojutsu toggle<gray> (" + (pr.dojutsuActive ? "active" : "inactive") + ")");
    }

    private String clanPerks(Clan c, int lv) {
        return switch (c) {
            case UCHIHA -> "dodge " + Math.round((plugin.getConfig().getDouble("sharingan.dodge-chance", 0.20) + 0.05 * (lv - 1)) * 100) + "%, fire jutsu -20% chakra";
            case HYUGA -> "Byakugan range " + (24 + 8 * (lv - 1)) + " blocks, Gentle Fist drains " + (25 + 10 * (lv - 1)) + " chakra";
            case UZUMAKI -> "chakra and regen x" + String.format(Locale.ROOT, "%.2f", 1.25 + 0.25 * lv);
            case HATAKE -> "White Light Sabre costs " + (20 + 10 * (lv - 1)) + "% less";
            case NARA -> "Shadow Possession costs " + (10 * lv) + "% less";
        };
    }

    private static int slotOf(String s) {
        try {
            int n = Integer.parseInt(s.trim());
            return n >= 1 && n <= 9 ? n - 1 : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
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
            if (args.length == 1) out.addAll(List.of("list", "info", "cast", "bind", "unbind", "binds"));
            else if (args.length == 2 && (args[0].equalsIgnoreCase("cast") || args[0].equalsIgnoreCase("info"))) out.addAll(plugin.jutsus().ids());
            else if (args.length == 2 && (args[0].equalsIgnoreCase("bind") || args[0].equalsIgnoreCase("unbind"))) out.addAll(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9"));
            else if (args.length == 3 && args[0].equalsIgnoreCase("bind")) out.addAll(plugin.jutsus().ids());
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
                    out.addAll(List.of("clan", "nature", "learn", "forget", "scroll", "item", "paper", "chakra", "awaken", "level", "cooldown", "refill", "mastery", "roll", "info", "eternal", "reset", "mangekyo", "reload"));
                } else if (args.length == 3) {
                    players(out);
                } else if (args.length == 4) {
                    String sub = args[1].toLowerCase(Locale.ROOT);
                    switch (sub) {
                        case "clan" -> clans(out);
                        case "awaken" -> {
                            clans(out);
                            out.add("off");
                        }
                        case "tomoe", "level" -> out.addAll(List.of("1", "2", "3", "auto"));
                        case "mastery" -> {
                            out.add("all");
                            out.addAll(plugin.jutsus().ids());
                        }
                        case "eternal" -> out.addAll(List.of("on", "off"));
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
