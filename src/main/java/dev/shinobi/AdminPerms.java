package dev.shinobi;

import java.util.Map;
import java.util.function.Predicate;

/** Pure map from /shinobi admin subcommands to permission nodes. Unknown subcommands need the general admin node. */
public final class AdminPerms {
    public static final String ADMIN = "shinobismp.admin";
    public static final String CLAN = "shinobismp.admin.clan";
    public static final String BLOODLINE = "shinobismp.admin.bloodline";
    public static final String ABILITIES = "shinobismp.admin.abilities";
    public static final String PROGRESSION = "shinobismp.admin.progression";
    public static final String CHAKRA = "shinobismp.admin.chakra";
    public static final String COOLDOWNS = "shinobismp.admin.cooldowns";
    public static final String DATA = "shinobismp.admin.data";
    public static final String RELOAD = "shinobismp.admin.reload";
    public static final String AUDIT = "shinobismp.admin.audit";

    public static final java.util.List<String> ALL_NODES = java.util.List.of(
            ADMIN, CLAN, BLOODLINE, ABILITIES, PROGRESSION, CHAKRA, COOLDOWNS, DATA, RELOAD, AUDIT);

    private static final Map<String, String> BY_SUB = Map.ofEntries(
            Map.entry("clan", CLAN), Map.entry("bloodline", BLOODLINE), Map.entry("access", ABILITIES),
            Map.entry("ability", ABILITIES), Map.entry("learn", ABILITIES), Map.entry("forget", ABILITIES),
            Map.entry("scroll", ABILITIES), Map.entry("mastery", ABILITIES), Map.entry("mangekyo", ABILITIES),
            Map.entry("awaken", PROGRESSION), Map.entry("level", PROGRESSION), Map.entry("eternal", PROGRESSION),
            Map.entry("roll", PROGRESSION), Map.entry("nature", PROGRESSION),
            Map.entry("chakra", CHAKRA), Map.entry("refill", CHAKRA),
            Map.entry("cooldown", COOLDOWNS),
            Map.entry("info", DATA), Map.entry("reset", DATA), Map.entry("restore", DATA),
            Map.entry("reload", RELOAD), Map.entry("audit", AUDIT));

    private AdminPerms() {}

    public static String nodeFor(String sub) {
        String n = sub == null ? null : BY_SUB.get(sub.toLowerCase(java.util.Locale.ROOT));
        return n != null ? n : ADMIN;
    }

    public static boolean allowed(Predicate<String> has, String sub) {
        return has.test(nodeFor(sub));
    }
}
