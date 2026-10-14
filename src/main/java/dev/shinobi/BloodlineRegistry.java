package dev.shinobi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure registry of bloodline definitions with the built-in defaults. The five core clan identities are fixed here;
 * every rare bloodline ships DISABLED and with a narrow, owner-adjustable clan list (never all five clans).
 * The suggested clan lists for rare bloodlines are server-design choices, not canon claims.
 */
public final class BloodlineRegistry {
    private final Map<String, BloodlineDef> byId = new LinkedHashMap<>();

    public void register(BloodlineDef d) {
        byId.put(d.id.toLowerCase(Locale.ROOT), d);
    }

    public BloodlineDef get(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<BloodlineDef> all() {
        return byId.values();
    }

    public List<String> ids() {
        return new ArrayList<>(byId.keySet());
    }

    /** Problems an owner should fix: unknown parents, cycles, innate with a parent, enabled-but-unreachable bloodlines. */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        for (BloodlineDef d : byId.values()) {
            if (d.parent != null && get(d.parent) == null) problems.add(d.id + ": unknown parent '" + d.parent + "'");
            if (d.innate && d.parent != null) problems.add(d.id + ": an innate bloodline cannot have a parent");
            if (d.enabled && !d.anyClan && d.eligibleClans.isEmpty()) problems.add(d.id + ": enabled but no clan can ever hold it");
            Set<String> seen = new HashSet<>();
            BloodlineDef cur = d;
            while (cur != null && cur.parent != null) {
                if (!seen.add(cur.id.toLowerCase(Locale.ROOT))) {
                    problems.add(d.id + ": parent chain loops");
                    break;
                }
                cur = get(cur.parent);
            }
        }
        return problems;
    }

    private static BloodlineDef core(String id, String name, BloodlineDef.Rarity r, Clan c, boolean innate, String parent, boolean enabled, BloodlineDef.Kind kind) {
        return BloodlineDef.builder(id, name, kind).rarity(r).clans(c).innate(innate).parent(parent).enabled(enabled).build();
    }

    private static BloodlineDef rare(String id, String name, BloodlineDef.Rarity r, Clan... clans) {
        return BloodlineDef.builder(id, name, BloodlineDef.Kind.RARE).rarity(r).clans(clans).enabled(false).build();
    }

    public static BloodlineRegistry defaults() {
        BloodlineRegistry r = new BloodlineRegistry();
        BloodlineDef.Kind core = BloodlineDef.Kind.CORE;
        BloodlineDef.Rarity rare = BloodlineDef.Rarity.RARE, epic = BloodlineDef.Rarity.EPIC, leg = BloodlineDef.Rarity.LEGENDARY;
        // ---- the five core clan identities ----
        r.register(core("sharingan", "Sharingan", rare, Clan.UCHIHA, true, null, true, core));
        r.register(core("mangekyo_sharingan", "Mangekyo Sharingan", epic, Clan.UCHIHA, false, "sharingan", true, core));
        r.register(core("eternal_mangekyo", "Eternal Mangekyo Sharingan", leg, Clan.UCHIHA, false, "mangekyo_sharingan", true, core));
        r.register(core("rinnegan", "Rinnegan", leg, Clan.UCHIHA, false, "eternal_mangekyo", false, BloodlineDef.Kind.RARE));
        r.register(core("byakugan", "Byakugan", rare, Clan.HYUGA, true, null, true, core));
        r.register(core("tenseigan", "Tenseigan", leg, Clan.HYUGA, false, "byakugan", false, BloodlineDef.Kind.RARE));
        r.register(core("wood_release", "Wood Release", epic, Clan.SENJU, true, null, true, core));
        r.register(core("sealing_arts", "Sealing Arts", rare, Clan.UZUMAKI, true, null, true, core));
        r.register(core("shadow_manipulation", "Shadow Manipulation", rare, Clan.NARA, true, null, true, core));
        // ---- optional rare bloodlines: all disabled, narrow suggested clans, owner decides ----
        BloodlineDef.Rarity uncommon = BloodlineDef.Rarity.UNCOMMON;
        r.register(rare("ice_release", "Ice Release", epic, Clan.UZUMAKI));
        r.register(rare("lava_release", "Lava Release", epic, Clan.UCHIHA));
        r.register(rare("boil_release", "Boil Release", epic, Clan.UZUMAKI));
        r.register(rare("magnet_release", "Magnet Release", epic, Clan.NARA));
        r.register(rare("storm_release", "Storm Release", epic, Clan.UCHIHA));
        r.register(rare("scorch_release", "Scorch Release", epic, Clan.SENJU));
        r.register(rare("explosion_release", "Explosion Release", epic, Clan.UZUMAKI));
        r.register(rare("crystal_release", "Crystal Release", epic, Clan.SENJU));
        r.register(rare("dark_release", "Dark Release", epic, Clan.UCHIHA));
        r.register(rare("swift_release", "Swift Release", uncommon, Clan.HYUGA));
        r.register(rare("steel_release", "Steel Release", epic, Clan.NARA));
        r.register(rare("shikotsumyaku", "Shikotsumyaku (Bone)", leg));   // no clan by default: owner must configure
        r.register(rare("dust_release", "Dust Release", leg, Clan.HYUGA));
        return r;
    }
}
