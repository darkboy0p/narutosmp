package dev.shinobi;

import java.util.List;
import java.util.Locale;

/**
 * Pure mapping from a player's dojutsu state to the equipment-layer style drawn on their worn Shinobi Headband.
 * The keys in {@link #ALL} must each have an equipment asset and texture in the resource pack
 * (tools/verify_pack.py checks this by reading the list below).
 */
public final class EyeStyle {
    // verify_pack.py reads the quoted keys inside this block:
    // EYE-STYLES-BEGIN
    public static final List<String> ALL = List.of("none", "sharingan_1", "sharingan_2", "sharingan_3",
            "mangekyo_amaterasu", "mangekyo_tsukuyomi", "mangekyo_kamui", "mangekyo_susanoo",
            "eternal_mangekyo", "rinnegan", "byakugan", "tenseigan");
    // EYE-STYLES-END

    private static final List<String> MANGEKYO = List.of("amaterasu", "tsukuyomi", "kamui", "susanoo");

    private EyeStyle() {}

    public static String key(Clan clan, boolean active, int tomoe, String mangekyoAbility,
                             boolean eternal, boolean rinnegan, boolean tenseigan) {
        if (!active || clan == null) return "none";
        if (clan == Clan.UCHIHA) {
            if (rinnegan) return "rinnegan";
            if (eternal) return "eternal_mangekyo";
            if (mangekyoAbility != null) {
                String a = mangekyoAbility.toLowerCase(Locale.ROOT);
                if (MANGEKYO.contains(a)) return "mangekyo_" + a;
            }
            return "sharingan_" + Math.max(1, Math.min(3, tomoe));
        }
        if (clan == Clan.HYUGA) return tenseigan ? "tenseigan" : "byakugan";
        return "none";
    }

    /** Equipment asset id (namespace shinobi). "none" is the plain headband. */
    public static String assetId(String key) {
        return "none".equals(key) ? "headband" : "headband_" + key;
    }
}
