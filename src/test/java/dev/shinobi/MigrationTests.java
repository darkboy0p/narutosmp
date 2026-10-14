package dev.shinobi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tests the profile schema migrations against a map-backed document (no Bukkit). */
public final class MigrationTests {
    private static int run, failed;

    private static void check(String name, boolean ok) {
        run++;
        if (!ok) { failed++; System.out.println("FAIL: " + name); }
    }

    /** Map-backed Doc. */
    static final class MapDoc implements SchemaMigrator.Doc {
        final Map<String, Object> m = new LinkedHashMap<>();
        @Override public String getString(String k) { Object o = m.get(k); return o == null ? null : String.valueOf(o); }
        @Override public boolean getBoolean(String k, boolean d) { Object o = m.get(k); return o instanceof Boolean b ? b : d; }
        @Override public int getInt(String k, int d) { Object o = m.get(k); return o instanceof Number n ? n.intValue() : d; }
        @SuppressWarnings("unchecked")
        @Override public List<String> getStringList(String k) { Object o = m.get(k); return o instanceof List<?> l ? new ArrayList<>((List<String>) l) : new ArrayList<>(); }
        @Override public void set(String k, Object v) { m.put(k, v); }
        MapDoc put(String k, Object v) { m.put(k, v); return this; }
    }

    private static MapDoc legacyProfile(String clan) {
        return new MapDoc().put("rolled", true).put("nature", "FIRE").put("clan", clan).put("awakened", true)
                .put("max-chakra", 140.0).put("known", List.of("fireball", "chidori")).put("tomoe", 2)
                .put("weighted-seconds", 4321.5).put("binds", List.of("fireball", "", "chidori"))
                .put("completed-missions", List.of("a", "b")).put("bonds", List.of("11111111-1111-1111-1111-111111111111"));
    }

    private static boolean sameExcept(Map<String, Object> before, Map<String, Object> after, String... changed) {
        List<String> skip = List.of(changed);
        for (Map.Entry<String, Object> e : before.entrySet()) {
            if (skip.contains(e.getKey())) continue;
            if (!e.getValue().equals(after.get(e.getKey()))) return false;
        }
        return true;
    }

    public static void main(String[] args) {
        // --- Hatake -> Senju, v1 -> v3 ---
        MapDoc h = legacyProfile("HATAKE");
        Map<String, Object> before = new LinkedHashMap<>(h.m);
        check("v1 file reports version 1", SchemaMigrator.versionOf(h) == 1);
        check("hatake migration modifies", SchemaMigrator.migrate(h));
        check("clan is now SENJU", "SENJU".equals(h.getString("clan")));
        check("legacy clan recorded", "HATAKE".equals(h.getString("legacy-clan")));
        check("one history entry", h.getStringList("clan-history").size() == 1 && h.getStringList("clan-history").get(0).startsWith("HATAKE->SENJU"));
        check("version is current", SchemaMigrator.versionOf(h) == SchemaMigrator.CURRENT);
        check("bloodlines list created (empty for Senju)", h.m.containsKey("bloodlines") && h.getStringList("bloodlines").isEmpty());
        check("no other key changed or lost", sameExcept(before, h.m, "clan", "legacy-clan", "clan-history", "bloodlines", "forced-bloodlines", "forced-abilities", "mangekyo-powers", SchemaMigrator.KEY_VERSION));
        check("no keys disappeared", h.m.keySet().containsAll(before.keySet()));

        // --- idempotence ---
        Map<String, Object> once = new LinkedHashMap<>(h.m);
        check("second run reports no change", !SchemaMigrator.migrate(h));
        check("second run leaves data identical", once.equals(h.m));
        check("history not duplicated after re-run", h.getStringList("clan-history").size() == 1);

        // --- lowercase / padded legacy value ---
        MapDoc lower = legacyProfile("  hatake ");
        SchemaMigrator.migrate(lower);
        check("lowercase hatake migrates", "SENJU".equals(lower.getString("clan")));

        // --- a v2 file (already Senju) only gets v3 additions ---
        MapDoc v2 = legacyProfile("SENJU").put(SchemaMigrator.KEY_VERSION, 2).put("legacy-clan", "HATAKE").put("clan-history", List.of("HATAKE->SENJU | x | schema-2 migration"));
        Map<String, Object> v2Before = new LinkedHashMap<>(v2.m);
        check("v2 file migrates to current", SchemaMigrator.migrate(v2) && SchemaMigrator.versionOf(v2) == SchemaMigrator.CURRENT);
        check("v2 history untouched", v2.getStringList("clan-history").equals(v2Before.get("clan-history")));
        check("v2 other keys unchanged", sameExcept(v2Before, v2.m, "bloodlines", "forced-bloodlines", "forced-abilities", "mangekyo-powers", SchemaMigrator.KEY_VERSION));

        // --- Uchiha legacy Mangekyo seeds bloodlines ---
        MapDoc u = legacyProfile("UCHIHA").put("mangekyo", "amaterasu").put("eternal", true);
        SchemaMigrator.migrate(u);
        check("mangekyo seeded", u.getStringList("bloodlines").contains("mangekyo_sharingan"));
        check("eternal seeded", u.getStringList("bloodlines").contains("eternal_mangekyo"));
        check("seeded exactly once", u.getStringList("bloodlines").size() == 2);
        MapDoc plain = legacyProfile("UCHIHA");
        SchemaMigrator.migrate(plain);
        check("plain Sharingan user gets no Mangekyo", plain.getStringList("bloodlines").isEmpty());
        MapDoc wrong = legacyProfile("HYUGA").put("mangekyo", "kamui").put("eternal", true);
        SchemaMigrator.migrate(wrong);
        check("non-Uchiha with stray mangekyo data is not seeded", wrong.getStringList("bloodlines").isEmpty());
        check("stray data kept, not deleted", "kamui".equals(wrong.getString("mangekyo")));

        // --- existing grants are preserved ---
        MapDoc grants = legacyProfile("UZUMAKI").put("bloodlines", List.of("ice_release"));
        SchemaMigrator.migrate(grants);
        check("existing bloodline grants preserved", grants.getStringList("bloodlines").equals(List.of("ice_release")));

        // --- v3 -> v4: forced lists added, existing ones preserved ---
        MapDoc v3 = legacyProfile("NARA").put(SchemaMigrator.KEY_VERSION, 3).put("bloodlines", List.of("magnet_release"));
        Map<String, Object> v3Before = new LinkedHashMap<>(v3.m);
        check("v3 file migrates to current", SchemaMigrator.migrate(v3) && SchemaMigrator.versionOf(v3) == SchemaMigrator.CURRENT);
        check("v4 adds empty forced lists", v3.getStringList("forced-bloodlines").isEmpty() && v3.getStringList("forced-abilities").isEmpty());
        check("v3 data untouched by v4", sameExcept(v3Before, v3.m, "forced-bloodlines", "forced-abilities", "mangekyo-powers", SchemaMigrator.KEY_VERSION));
        MapDoc forced = legacyProfile("NARA").put(SchemaMigrator.KEY_VERSION, 3).put("forced-abilities", List.of("amaterasu")).put("forced-bloodlines", List.of("ice_release"));
        SchemaMigrator.migrate(forced);
        check("existing forced grants survive migration", forced.getStringList("forced-abilities").equals(List.of("amaterasu")) && forced.getStringList("forced-bloodlines").equals(List.of("ice_release")));
        MapDoc current = legacyProfile("NARA").put(SchemaMigrator.KEY_VERSION, SchemaMigrator.CURRENT).put("forced-abilities", List.of("amaterasu"));
        check("current-version file with forced grants is untouched", !SchemaMigrator.migrate(current) && current.getStringList("forced-abilities").equals(List.of("amaterasu")));

        // --- v4 -> v5: mangekyo-powers seeded from the legacy single power ---
        MapDoc v4 = legacyProfile("UCHIHA").put(SchemaMigrator.KEY_VERSION, 4).put("mangekyo", "susanoo").put("forced-abilities", List.of("amaterasu"));
        Map<String, Object> v4Before = new LinkedHashMap<>(v4.m);
        check("v4 file migrates to current", SchemaMigrator.migrate(v4) && SchemaMigrator.versionOf(v4) == SchemaMigrator.CURRENT);
        check("legacy mangekyo power seeds mangekyo-powers", v4.getStringList("mangekyo-powers").equals(List.of("susanoo")));
        check("legacy mangekyo field kept as the primary power", "susanoo".equals(v4.getString("mangekyo")));
        check("v5 leaves everything else alone", sameExcept(v4Before, v4.m, "mangekyo-powers", SchemaMigrator.KEY_VERSION));
        MapDoc noPower = legacyProfile("UCHIHA").put(SchemaMigrator.KEY_VERSION, 4);
        SchemaMigrator.migrate(noPower);
        check("no power -> empty list", noPower.getStringList("mangekyo-powers").isEmpty());
        MapDoc multi = legacyProfile("UCHIHA").put(SchemaMigrator.KEY_VERSION, 4).put("mangekyo", "susanoo").put("mangekyo-powers", List.of("amaterasu", "susanoo"));
        SchemaMigrator.migrate(multi);
        check("existing multi-power list preserved, primary not duplicated", multi.getStringList("mangekyo-powers").equals(List.of("amaterasu", "susanoo")));
        Map<String, Object> afterV5 = new LinkedHashMap<>(v4.m);
        check("v5 migration is idempotent", !SchemaMigrator.migrate(v4) && afterV5.equals(v4.m));

        // --- newer files are never modified ---
        MapDoc future = legacyProfile("HATAKE").put(SchemaMigrator.KEY_VERSION, SchemaMigrator.CURRENT + 5);
        Map<String, Object> fBefore = new LinkedHashMap<>(future.m);
        check("future schema untouched", !SchemaMigrator.migrate(future) && fBefore.equals(future.m));

        // --- brand-new empty profile ---
        MapDoc empty = new MapDoc();
        check("empty doc migrates safely", SchemaMigrator.migrate(empty) && SchemaMigrator.versionOf(empty) == SchemaMigrator.CURRENT);
        check("empty doc gets no clan invented", empty.getString("clan") == null);

        System.out.println(run + " checks, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
