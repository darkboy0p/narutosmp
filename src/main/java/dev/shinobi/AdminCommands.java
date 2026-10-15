package dev.shinobi;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * /shinobi admin bloodline | ability | access | audit. Every argument is validated, every change is audited, and offline
 * edits are opt-in. Permissions are checked by the caller per subcommand ({@link AdminPerms}); this class never grants anything itself.
 */
public final class AdminCommands {
    private static final List<String> SUBS = List.of("bloodline", "ability", "access", "audit");
    private final ShinobiPlugin plugin;

    public AdminCommands(ShinobiPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean handles(String sub) {
        return SUBS.contains(sub);
    }

    private record Target(UUID id, String name, Profile pr, boolean online) {}

    // ------------------------------------------------------------------ plumbing

    private Target resolve(CommandSender s, String token) {
        Player online = Bukkit.getPlayerExact(token);
        UUID id = null;
        if (online == null) {
            id = AdminInput.uuid(token);
            if (id != null) online = Bukkit.getPlayer(id);
        }
        if (online != null) return new Target(online.getUniqueId(), online.getName(), plugin.profiles().get(online), true);
        if (!plugin.getConfig().getBoolean("admin.allow-offline-edits", false)) {
            Msg.send(s, "<red>That player is not online (offline edits are disabled: admin.allow-offline-edits).");
            return null;
        }
        if (id == null && AdminInput.validName(token)) {
            OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(token);
            if (op != null) id = op.getUniqueId();
        }
        if (id == null || !plugin.profiles().exists(id)) {
            Msg.send(s, "<red>No stored profile found for that player.");
            return null;
        }
        return new Target(id, token, plugin.profiles().get(id), false);
    }

    private void finish(Target t) {
        if (t.online()) plugin.profiles().save(t.pr());
        else plugin.profiles().unload(t.id()); // saves, then drops the temporary offline profile from the cache
    }

    private void audit(CommandSender s, Target t, String action, String prev, String now, boolean forced, boolean ok, String reason) {
        Player p = s instanceof Player pl ? pl : null;
        plugin.audit().record(new AuditEntry(Instant.now(), p != null ? p.getName() : "CONSOLE", p != null ? p.getUniqueId().toString() : null,
                t == null ? null : t.name(), t == null ? null : t.id().toString(), action, prev, now, forced, ok, reason));
    }

    private boolean needConfirm(CommandSender s, String[] a, int idx, String what) {
        if (!plugin.getConfig().getBoolean("admin.require-confirmation", true)) return false;
        if (a.length > idx && a[idx].equalsIgnoreCase("confirm")) return false;
        Msg.send(s, "<yellow>This will " + what + ". Repeat the command with <white>confirm<yellow> at the end to proceed.");
        return true;
    }

    // ------------------------------------------------------------------ dispatch

    public void run(CommandSender s, String[] a) {
        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "audit" -> audit(s, a);
            case "access" -> access(s, a);
            case "bloodline" -> bloodline(s, a);
            case "ability" -> ability(s, a);
            default -> { }
        }
    }

    private void audit(CommandSender s, String[] a) {
        int n = 10;
        if (a.length > 1) {
            OptionalDouble d = AdminInput.number(a[1], 1, 50);
            if (d.isEmpty()) {
                Msg.send(s, "<red>Usage: /shinobi admin audit [1-50]");
                return;
            }
            n = (int) d.getAsDouble();
        }
        List<String> lines = plugin.audit().tail(n);
        Msg.send(s, "<gray>Last " + lines.size() + " audit entr" + (lines.size() == 1 ? "y" : "ies") + ":");
        for (String l : lines) Msg.send(s, "<dark_gray>" + l.replace("<", "\\<"));
    }

    private void access(CommandSender s, String[] a) {
        if (a.length < 3) {
            Msg.send(s, "<red>Usage: /shinobi admin access [player] [ability-id]  (explains why an ability is allowed or denied)");
            return;
        }
        Target t = resolve(s, a[1]);
        if (t == null) return;
        String id = AdminInput.id(a[2]);
        Jutsu j = id == null ? null : plugin.jutsus().get(id);
        if (j == null) Msg.send(s, "<red>Unknown ability. Valid ids: " + String.join(", ", plugin.jutsus().ids()));
        else for (String line : plugin.access().explain(t.pr(), j)) Msg.send(s, "<gray>" + line);
        if (!t.online()) plugin.profiles().unload(t.id());
    }

    // ------------------------------------------------------------------ bloodlines

    private void bloodline(CommandSender s, String[] a) {
        String usage = "<red>Usage: /shinobi admin bloodline [player] <list|grant|force|revoke|clear|eligibility> [bloodline-id]";
        if (a.length < 3) {
            Msg.send(s, usage);
            return;
        }
        Target t = resolve(s, a[1]);
        if (t == null) return;
        Profile pr = t.pr();
        String op = a[2].toLowerCase(Locale.ROOT);
        String id = a.length > 3 ? AdminInput.id(a[3]) : null;
        BloodlineDef def = id == null ? null : plugin.bloodlines().registry().get(id);
        boolean needsId = op.equals("grant") || op.equals("force") || op.equals("revoke") || op.equals("eligibility");
        if (needsId && def == null) {
            Msg.send(s, "<red>Unknown bloodline. Valid ids: " + String.join(", ", plugin.bloodlines().registry().ids()));
            if (!t.online()) plugin.profiles().unload(t.id());
            return;
        }
        switch (op) {
            case "list" -> {
                for (String line : plugin.bloodlines().describe(pr)) Msg.send(s, "<gray>" + line);
                Msg.send(s, "<gray>Forced bloodlines: <white>" + (pr.forcedBloodlines.isEmpty() ? "none" : String.join(", ", pr.forcedBloodlines)));
                if (!t.online()) plugin.profiles().unload(t.id());
            }
            case "eligibility" -> {
                BloodlineRules.Result r = BloodlineRules.check(plugin.bloodlines().registry(), def.id, pr.clan, pr.awakened, plugin.bloodlines().unlockedFor(pr));
                Msg.send(s, "<gray>" + def.display + " (natural rules): <white>" + r + " - " + BloodlineRules.describe(r));
                Msg.send(s, "<gray>Forced: <white>" + pr.forcedBloodlines.contains(def.id) + "<gray>, effective result: <white>" + plugin.bloodlines().check(pr, def.id));
                if (!t.online()) plugin.profiles().unload(t.id());
            }
            case "grant" -> {
                String before = String.valueOf(pr.bloodlines);
                String msg = plugin.bloodlines().grant(pr, def.id);
                boolean ok = msg.startsWith("Granted");
                Msg.send(s, (ok ? "<green>" : "<red>") + msg);
                audit(s, t, "bloodline.grant:" + def.id, before, String.valueOf(pr.bloodlines), false, ok, ok ? null : msg);
                finish(t);
            }
            case "force" -> {
                String before = String.valueOf(pr.forcedBloodlines);
                pr.forcedBloodlines.add(def.id);
                Msg.send(s, "<green>Force-granted " + def.display + " to " + t.name() + ". It is stored explicitly and survives relogs, restarts, reloads and clan changes. "
                        + "It opens only abilities tagged with this bloodline, and never bypasses chakra, cooldowns, PvP or destination checks.");
                audit(s, t, "bloodline.force:" + def.id, before, String.valueOf(pr.forcedBloodlines), true, true, null);
                finish(t);
            }
            case "revoke" -> {
                String before = pr.bloodlines + "|" + pr.forcedBloodlines;
                boolean a1 = pr.bloodlines.remove(def.id), a2 = pr.forcedBloodlines.remove(def.id);
                Msg.send(s, (a1 || a2) ? "<green>Revoked " + def.display + (a2 ? " (forced grant removed)" : "") + "." : "<red>" + t.name() + " has no stored grant for that bloodline (innate and legacy Mangekyo stages are not stored grants).");
                audit(s, t, "bloodline.revoke:" + def.id, before, pr.bloodlines + "|" + pr.forcedBloodlines, a2, a1 || a2, (a1 || a2) ? null : "nothing to revoke");
                finish(t);
            }
            case "clear" -> {
                if (needConfirm(s, a, 3, "remove ALL stored and forced bloodline grants from " + t.name())) {
                    if (!t.online()) plugin.profiles().unload(t.id());
                    return;
                }
                String before = pr.bloodlines + "|" + pr.forcedBloodlines;
                pr.bloodlines.clear();
                pr.forcedBloodlines.clear();
                Msg.send(s, "<green>Cleared all stored bloodline grants for " + t.name() + ".");
                audit(s, t, "bloodline.clear", before, "[]|[]", !before.equals("[]|[]"), true, null);
                finish(t);
            }
            default -> {
                Msg.send(s, usage);
                if (!t.online()) plugin.profiles().unload(t.id());
            }
        }
    }

    // ------------------------------------------------------------------ abilities

    private void ability(CommandSender s, String[] a) {
        String usage = "<red>Usage: /shinobi admin ability [player] <list|grant|force|revoke|clear|cooldown|resetcooldowns> [ability-id] [seconds]";
        if (a.length < 3) {
            Msg.send(s, usage);
            return;
        }
        Target t = resolve(s, a[1]);
        if (t == null) return;
        Profile pr = t.pr();
        String op = a[2].toLowerCase(Locale.ROOT);
        String id = a.length > 3 ? AdminInput.id(a[3]) : null;
        Jutsu j = id == null ? null : plugin.jutsus().get(id);
        boolean needsId = op.equals("grant") || op.equals("force") || op.equals("revoke") || op.equals("cooldown");
        if (needsId && j == null) {
            Msg.send(s, "<red>Unknown ability. Valid ids: " + String.join(", ", plugin.jutsus().ids()));
            if (!t.online()) plugin.profiles().unload(t.id());
            return;
        }
        switch (op) {
            case "list" -> {
                Msg.send(s, "<gray>Learned: <white>" + (pr.known.isEmpty() ? "none" : String.join(", ", pr.known)));
                Msg.send(s, "<gray>Forced: <white>" + (pr.forcedAbilities.isEmpty() ? "none" : String.join(", ", pr.forcedAbilities)));
                if (!t.online()) plugin.profiles().unload(t.id());
            }
            case "grant" -> {
                if (!j.needsKnown) {
                    Msg.send(s, "<yellow>" + j.name + " does not need to be learned; access is decided by clan and bloodline. Use <white>force<yellow> to override those.");
                    audit(s, t, "ability.grant:" + j.id, null, null, false, false, "ability needs no unlock");
                    if (!t.online()) plugin.profiles().unload(t.id());
                    return;
                }
                String before = String.valueOf(pr.known);
                pr.known.add(j.id);
                Msg.send(s, "<green>" + t.name() + " has learned " + j.name + ".");
                audit(s, t, "ability.grant:" + j.id, before, String.valueOf(pr.known), false, true, null);
                finish(t);
            }
            case "force" -> {
                String before = String.valueOf(pr.forcedAbilities);
                pr.forcedAbilities.add(j.id);
                Msg.send(s, "<green>Force-granted " + j.name + " to " + t.name() + ": clan, bloodline, learned, nature and prerequisite checks are skipped for this ability only. "
                        + "Chakra, cooldowns, sealing, world rules, PvP and destination checks still apply.");
                audit(s, t, "ability.force:" + j.id, before, String.valueOf(pr.forcedAbilities), true, true, null);
                finish(t);
            }
            case "revoke" -> {
                String before = pr.known + "|" + pr.forcedAbilities;
                boolean a1 = pr.known.remove(j.id), a2 = pr.forcedAbilities.remove(j.id);
                Msg.send(s, (a1 || a2) ? "<green>Revoked " + j.name + "." : "<red>" + t.name() + " has no stored grant for that ability.");
                audit(s, t, "ability.revoke:" + j.id, before, pr.known + "|" + pr.forcedAbilities, a2, a1 || a2, (a1 || a2) ? null : "nothing to revoke");
                finish(t);
            }
            case "clear" -> {
                if (needConfirm(s, a, 3, "remove ALL forced abilities from " + t.name() + " (learned scrolls are kept)")) {
                    if (!t.online()) plugin.profiles().unload(t.id());
                    return;
                }
                String before = String.valueOf(pr.forcedAbilities);
                pr.forcedAbilities.clear();
                Msg.send(s, "<green>Cleared forced abilities for " + t.name() + ".");
                audit(s, t, "ability.clear", before, "[]", !before.equals("[]"), true, null);
                finish(t);
            }
            case "cooldown" -> {
                OptionalDouble sec = a.length > 4 ? AdminInput.number(a[4], 0, 86_400) : OptionalDouble.empty();
                if (sec.isEmpty()) {
                    Msg.send(s, "<red>Usage: /shinobi admin ability [player] cooldown [ability-id] [0-86400 seconds]");
                    if (!t.online()) plugin.profiles().unload(t.id());
                    return;
                }
                Long prev = pr.cooldowns.get(j.id);
                if (sec.getAsDouble() <= 0) pr.cooldowns.remove(j.id);
                else pr.cooldowns.put(j.id, System.currentTimeMillis() + (long) (sec.getAsDouble() * 1000));
                Msg.send(s, "<green>Set the current " + j.name + " cooldown for " + t.name() + " to " + sec.getAsDouble() + "s (one-off; the ability's normal cooldown applies after the next cast).");
                audit(s, t, "ability.cooldown:" + j.id, String.valueOf(prev), String.valueOf(pr.cooldowns.get(j.id)), false, true, null);
                finish(t);
            }
            case "resetcooldowns" -> {
                int n = pr.cooldowns.size();
                pr.cooldowns.clear();
                Msg.send(s, "<green>Cleared " + n + " cooldown(s) for " + t.name() + ".");
                audit(s, t, "ability.resetcooldowns", String.valueOf(n), "0", false, true, null);
                finish(t);
            }
            default -> {
                Msg.send(s, usage);
                if (!t.online()) plugin.profiles().unload(t.id());
            }
        }
    }

    // ------------------------------------------------------------------ tab completion (online names only)

    public List<String> tab(String[] args) {
        List<String> out = new ArrayList<>();
        String sub = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 3) {
            if (!sub.equals("audit")) for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (args.length == 4) {
            switch (sub) {
                case "bloodline" -> out.addAll(List.of("list", "grant", "force", "revoke", "clear", "eligibility"));
                case "ability" -> out.addAll(List.of("list", "grant", "force", "revoke", "clear", "cooldown", "resetcooldowns"));
                case "access" -> out.addAll(plugin.jutsus().ids());
                default -> { }
            }
        } else if (args.length == 5) {
            String op = args[3].toLowerCase(Locale.ROOT);
            if (sub.equals("bloodline") && List.of("grant", "force", "revoke", "eligibility").contains(op)) out.addAll(plugin.bloodlines().registry().ids());
            if (sub.equals("ability") && List.of("grant", "force", "revoke", "cooldown").contains(op)) out.addAll(plugin.jutsus().ids());
            if ((sub.equals("bloodline") || sub.equals("ability")) && op.equals("clear")) out.add("confirm");
        }
        return out;
    }
}
