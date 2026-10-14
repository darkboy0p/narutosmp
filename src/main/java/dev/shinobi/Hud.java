package dev.shinobi;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Set;

/** Resource-pack helpers: HUD glyphs (custom font "shinobi:hud") and custom item models. */
public final class Hud {
    public static final char BAR_FULL = '\uE100';
    public static final char BAR_EMPTY = '\uE101';

    /** Order must match EYE_ORDER in tools/make_pack.py (glyphs start at U+E200). */
    private static final List<String> EYE_ORDER = List.of(
            "eye_none", "eye_sharingan_1", "eye_sharingan_2", "eye_sharingan_3", "eye_mangekyo_amaterasu",
            "eye_mangekyo_tsukuyomi", "eye_mangekyo_kamui", "eye_mangekyo_susanoo", "eye_eternal_mangekyo",
            "eye_byakugan", "eye_rinnegan");

    /** Jutsu that have their own scroll texture in the pack. */
    public static final Set<String> SCROLL_MODELS = Set.of(
            "great_fireball", "phoenix_flower", "water_dragon", "water_wall", "wind_blade", "great_breakthrough",
            "chidori", "earth_wall", "substitution", "rasengan", "rasenshuriken", "shadow_clone", "body_flicker", "chakra_chains",
            "white_light_sabre", "shadow_bind", "mangekyo_ability", "wall_walk", "water_walk");

    private final ShinobiPlugin plugin;

    public Hud(ShinobiPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean on() {
        return plugin.getConfig().getBoolean("resource-pack.enabled", false);
    }

    // ------------------------------------------------------------------ glyphs

    private static String glyph(char c) {
        return "<font:shinobi:hud>" + c + "</font>";
    }

    public String seal(Seal s) {
        return glyph((char) (0xE000 + s.ordinal()));
    }

    public String bar(int filled, int total) {
        return "<font:shinobi:hud>" + String.valueOf(BAR_FULL).repeat(Math.max(0, filled))
                + String.valueOf(BAR_EMPTY).repeat(Math.max(0, total - filled)) + "</font>";
    }

    public String natureGlyph(Nature n) {
        return glyph((char) (0xE300 + n.ordinal()));
    }

    public String gateGlyph(int gate) {
        return glyph((char) (0xE400 + Math.max(0, Math.min(7, gate - 1))));
    }

    public String eyeGlyph(Profile pr) {
        int idx = EYE_ORDER.indexOf(eyeKey(pr));
        return glyph((char) (0xE200 + Math.max(0, idx)));
    }

    // ------------------------------------------------------------------ eyes

    /** Clan level 1-3 (Sharingan tomoe for Uchiha) from training, or the admin override. */
    public int sharinganStage(Profile pr) {
        if (pr.tomoe >= 1) return Math.min(3, pr.tomoe);
        int total = 0;
        for (int v : pr.mastery.values()) total += v;
        return total >= 75 ? 3 : total >= 25 ? 2 : 1;
    }

    /** Which eye texture represents this player's dojutsu right now. */
    public String eyeKey(Profile pr) {
        if (pr.clan == Clan.UCHIHA && pr.awakened) {
            if (pr.mangekyo != null) return pr.eternal ? "eye_eternal_mangekyo" : "eye_mangekyo_" + pr.mangekyo;
            return "eye_sharingan_" + sharinganStage(pr);
        }
        if (pr.clan == Clan.HYUGA && pr.awakened) return "eye_byakugan";
        return "eye_none";
    }

    public String eyeName(Profile pr) {
        String k = eyeKey(pr);
        return switch (k) {
            case "eye_none" -> "No dojutsu";
            case "eye_byakugan" -> "Byakugan (level " + sharinganStage(pr) + ")";
            case "eye_eternal_mangekyo" -> "Eternal Mangekyou Sharingan (" + MangekyoManager.display(pr.mangekyo) + ")";
            default -> k.startsWith("eye_mangekyo_")
                    ? "Mangekyou Sharingan (" + MangekyoManager.display(pr.mangekyo) + ")"
                    : "Sharingan (" + sharinganStage(pr) + " tomoe)";
        };
    }

    // ------------------------------------------------------------------ item models

    /** Applies a custom item model from the pack (no-op when the pack is disabled). */
    public ItemStack model(ItemStack it, String name) {
        if (!on()) return it;
        ItemMeta m = it.getItemMeta();
        m.setItemModel(new NamespacedKey("shinobi", name));
        it.setItemMeta(m);
        return it;
    }

    public String scrollModel(String jutsuId) {
        return SCROLL_MODELS.contains(jutsuId) ? "scroll_" + jutsuId : "jutsu_scroll";
    }
}
