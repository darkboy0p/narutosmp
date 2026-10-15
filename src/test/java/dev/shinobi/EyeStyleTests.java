package dev.shinobi;

import java.util.HashSet;
import java.util.Set;

/** Tests the dojutsu-state -> headband-layer mapping. */
public final class EyeStyleTests {
    private static int run, failed;

    private static void check(String name, boolean ok) {
        run++;
        if (!ok) { failed++; System.out.println("FAIL: " + name); }
    }

    public static void main(String[] args) {
        check("inactive uchiha shows plain headband", EyeStyle.key(Clan.UCHIHA, false, 3, "kamui", true, true, false).equals("none"));
        check("no clan shows plain headband", EyeStyle.key(null, true, 3, null, false, false, false).equals("none"));
        for (Clan c : new Clan[]{Clan.SENJU, Clan.UZUMAKI, Clan.NARA}) {
            check(c + " has no eye layer", EyeStyle.key(c, true, 3, "kamui", true, true, true).equals("none"));
        }
        check("tomoe 1", EyeStyle.key(Clan.UCHIHA, true, 1, null, false, false, false).equals("sharingan_1"));
        check("tomoe 2", EyeStyle.key(Clan.UCHIHA, true, 2, null, false, false, false).equals("sharingan_2"));
        check("tomoe 3", EyeStyle.key(Clan.UCHIHA, true, 3, null, false, false, false).equals("sharingan_3"));
        check("tomoe clamps low", EyeStyle.key(Clan.UCHIHA, true, 0, null, false, false, false).equals("sharingan_1"));
        check("tomoe clamps high", EyeStyle.key(Clan.UCHIHA, true, 9, null, false, false, false).equals("sharingan_3"));
        for (String a : new String[]{"amaterasu", "tsukuyomi", "kamui", "susanoo"}) {
            check("mangekyo " + a, EyeStyle.key(Clan.UCHIHA, true, 3, a, false, false, false).equals("mangekyo_" + a));
        }
        check("mangekyo ability is case-insensitive", EyeStyle.key(Clan.UCHIHA, true, 3, "KAMUI", false, false, false).equals("mangekyo_kamui"));
        check("unknown mangekyo ability falls back to tomoe", EyeStyle.key(Clan.UCHIHA, true, 2, "bogus", false, false, false).equals("sharingan_2"));
        check("eternal beats mangekyo", EyeStyle.key(Clan.UCHIHA, true, 3, "kamui", true, false, false).equals("eternal_mangekyo"));
        check("rinnegan beats eternal", EyeStyle.key(Clan.UCHIHA, true, 3, "kamui", true, true, false).equals("rinnegan"));
        check("tenseigan flag does not affect Uchiha", EyeStyle.key(Clan.UCHIHA, true, 1, null, false, false, true).equals("sharingan_1"));
        check("byakugan", EyeStyle.key(Clan.HYUGA, true, 1, null, false, false, false).equals("byakugan"));
        check("tenseigan", EyeStyle.key(Clan.HYUGA, true, 1, null, false, false, true).equals("tenseigan"));
        check("sharingan flags do not affect Hyuga", EyeStyle.key(Clan.HYUGA, true, 3, "kamui", true, true, false).equals("byakugan"));

        // every reachable key is a declared style; asset ids are unique
        Set<String> reached = new HashSet<>();
        for (Clan c : Clan.values()) for (boolean active : new boolean[]{false, true}) for (int t = 0; t <= 4; t++)
            for (String m : new String[]{null, "amaterasu", "tsukuyomi", "kamui", "susanoo", "x"})
                for (boolean e : new boolean[]{false, true}) for (boolean r : new boolean[]{false, true}) for (boolean te : new boolean[]{false, true})
                    reached.add(EyeStyle.key(c, active, t, m, e, r, te));
        check("every reachable key is declared", EyeStyle.ALL.containsAll(reached));
        check("every declared key is reachable", reached.containsAll(EyeStyle.ALL));
        Set<String> ids = new HashSet<>();
        for (String k : EyeStyle.ALL) ids.add(EyeStyle.assetId(k));
        check("asset ids unique", ids.size() == EyeStyle.ALL.size());
        check("none maps to plain headband", EyeStyle.assetId("none").equals("headband"));
        check("others are prefixed", EyeStyle.assetId("byakugan").equals("headband_byakugan"));

        System.out.println(run + " checks, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
