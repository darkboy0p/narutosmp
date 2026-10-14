package dev.shinobi;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** Tests for admin permissions, input validation, forced-access rules and audit entries. */
public final class AdminLogicTests {
    private static int run, failed;

    private static void check(String name, boolean ok) {
        run++;
        if (!ok) { failed++; System.out.println("FAIL: " + name); }
    }

    private static AccessRules.Decision decide(String ability, String bloodline, Set<String> fa, Set<String> fb) {
        return AccessRules.decide(new AccessRules.Input(ability, bloodline, fa, fb));
    }

    public static void main(String[] args) {
        // ---------------- permissions ----------------
        String[] subs = {"clan", "bloodline", "ability", "access", "learn", "forget", "scroll", "mastery", "mangekyo", "awaken",
                "level", "eternal", "roll", "nature", "chakra", "refill", "cooldown", "info", "reset", "restore", "reload", "audit"};
        for (String s : subs) check(s + " maps to a shinobismp.admin.* node", AdminPerms.nodeFor(s).startsWith("shinobismp.admin"));
        check("unknown subcommand needs the general admin node", AdminPerms.nodeFor("whatever").equals(AdminPerms.ADMIN));
        check("null subcommand needs the general admin node", AdminPerms.nodeFor(null).equals(AdminPerms.ADMIN));
        check("case-insensitive", AdminPerms.nodeFor("BLOODLINE").equals(AdminPerms.BLOODLINE));
        Predicate<String> none = n -> false;
        Predicate<String> onlyClan = n -> n.equals(AdminPerms.CLAN);
        Predicate<String> onlyAudit = n -> n.equals(AdminPerms.AUDIT);
        for (String s : subs) check("ordinary player denied " + s, !AdminPerms.allowed(none, s));
        check("clan node opens clan", AdminPerms.allowed(onlyClan, "clan"));
        check("clan node does NOT open bloodline", !AdminPerms.allowed(onlyClan, "bloodline"));
        check("clan node does NOT open chakra", !AdminPerms.allowed(onlyClan, "chakra"));
        check("audit node does not open data resets", !AdminPerms.allowed(onlyAudit, "reset"));
        check("audit node opens audit", AdminPerms.allowed(onlyAudit, "audit"));
        check("ten distinct nodes", new HashSet<>(AdminPerms.ALL_NODES).size() == 10);

        // ---------------- input validation ----------------
        check("plain id ok", "sharingan_dodge".equals(AdminInput.id("Sharingan_Dodge")));
        for (String bad : new String[]{null, "", " ", "../etc/passwd", "a/b", "a b", "a\\b", "x;y", "id\n", "é", "a".repeat(49), "..", "a.b"})
            check("bad id rejected: " + String.valueOf(bad).replace("\n", "\\n"), AdminInput.id(bad) == null);
        check("valid name", AdminInput.validName("Mahdi_01"));
        check("name with space rejected", !AdminInput.validName("a b"));
        check("overlong name rejected", !AdminInput.validName("a".repeat(17)));
        UUID u = UUID.randomUUID();
        check("uuid round trip", u.equals(AdminInput.uuid(u.toString())));
        check("short uuid rejected", AdminInput.uuid("1234") == null);
        check("garbage uuid rejected", AdminInput.uuid("zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz") == null);
        check("number ok", AdminInput.number("12.5", 0, 100).getAsDouble() == 12.5);
        check("negative ok when allowed", AdminInput.number("-5", -10, 10).getAsDouble() == -5);
        for (String bad : new String[]{"NaN", "Infinity", "-Infinity", "1e9", "0x10", "1,5", "", "  ", "--1", "1.", ".5", "99999999999999999999999", "5 "})
            check("bad number rejected: [" + bad + "]", AdminInput.number(bad, -1e9, 1e9).isEmpty());
        check("above max rejected", AdminInput.number("101", 0, 100).isEmpty());
        check("below min rejected", AdminInput.number("-1", 0, 100).isEmpty());
        check("null number rejected", AdminInput.number(null, 0, 1).isEmpty());

        // ---------------- forced access rules ----------------
        Set<String> none2 = Set.of();
        AccessRules.Decision nat = decide("amaterasu", "mangekyo_sharingan", none2, none2);
        check("no force = natural, nothing skipped", nat.source() == AccessRules.Source.NATURAL && !nat.forced() && !nat.skipClan() && !nat.skipBloodline() && !nat.skipKnown());
        AccessRules.Decision fa = decide("amaterasu", "mangekyo_sharingan", Set.of("amaterasu"), none2);
        check("forced ability skips every access check", fa.source() == AccessRules.Source.FORCED_ABILITY && fa.skipClan() && fa.skipBloodline() && fa.skipKnown() && fa.skipNature() && fa.skipProgression());
        // a Nara player with forced 'amaterasu': an unrelated Uchiha ability stays locked
        AccessRules.Decision other = decide("susanoo", "mangekyo_sharingan", Set.of("amaterasu"), none2);
        check("forcing one ability does not open another", !other.forced());
        AccessRules.Decision fb = decide("susanoo", "mangekyo_sharingan", none2, Set.of("mangekyo_sharingan"));
        check("forced bloodline skips clan + bloodline only", fb.source() == AccessRules.Source.FORCED_BLOODLINE && fb.skipClan() && fb.skipBloodline() && !fb.skipKnown() && !fb.skipNature() && !fb.skipProgression());
        check("forced bloodline does not open other bloodlines", !decide("chidori", "sealing_arts", none2, Set.of("mangekyo_sharingan")).forced());
        check("forced bloodline does not open untagged abilities", !decide("fireball", null, none2, Set.of("mangekyo_sharingan")).forced());
        check("ability force beats bloodline force", decide("amaterasu", "mangekyo_sharingan", Set.of("amaterasu"), Set.of("mangekyo_sharingan")).source() == AccessRules.Source.FORCED_ABILITY);
        check("null ability id is natural", !decide(null, null, Set.of("x"), Set.of("y")).forced());

        // forced grants are plain stored sets: a clan change or reload does not touch them (simulated)
        Set<String> stored = new HashSet<>(Set.of("amaterasu"));
        Clan clan = Clan.NARA; clan = Clan.SENJU;           // clan change
        check("forced ability survives a clan change", decide("amaterasu", null, stored, none2).forced() && clan == Clan.SENJU);
        stored.remove("amaterasu");
        check("revoke removes only that grant", !decide("amaterasu", null, stored, none2).forced());

        // ---------------- audit entries ----------------
        AuditEntry e = new AuditEntry(Instant.parse("2026-10-12T10:00:00Z"), "Admin", UUID.randomUUID().toString(), "Target", UUID.randomUUID().toString(),
                "bloodline.force", "[]", "[ice_release]", true, true, null);
        String j = e.toJson();
        check("json has all fields", j.contains("\"time\":\"2026-10-12T10:00:00Z\"") && j.contains("\"action\":\"bloodline.force\"") && j.contains("\"forced\":true") && j.contains("\"success\":true") && j.contains("\"reason\":null"));
        check("json is one line", !j.contains("\n"));
        AuditEntry evil = new AuditEntry(Instant.EPOCH, "a\"b", null, "line1\nline2", null, "x\\y", "tab\t", "\u0001ctl\u2028", false, false, "quote \" and \\");
        String ej = evil.toJson();
        check("escaped quote", ej.contains("a\\\"b"));
        check("escaped newline stays on one line", ej.contains("line1\\nline2") && !ej.contains("\n"));
        check("escaped backslash", ej.contains("x\\\\y"));
        check("escaped control characters", ej.contains("\\u0001") && ej.contains("\\u2028"));
        check("null admin uuid becomes JSON null", ej.contains("\"adminUuid\":null"));
        check("failure recorded", ej.contains("\"success\":false") && ej.contains("\"forced\":false"));

        System.out.println(run + " checks, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
