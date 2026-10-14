package dev.shinobi;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Versioned, idempotent migrations for player profile files. Pure (works on a tiny {@link Doc} view), so it is unit-tested.
 *
 * <ul>
 *   <li>v1 (no {@code schema-version} key): original v0.6 layout.</li>
 *   <li>v2: Hatake clan replaced by Senju. {@code legacy-clan} records the old value and {@code clan-history} gets one entry.</li>

 *   <li>v5: {@code mangekyo-powers} list (a player may hold several Mangekyo powers), seeded from the legacy single {@code mangekyo}.</li>
 *   <li>v4: explicit {@code forced-bloodlines} / {@code forced-abilities} lists (administrator-forced access). Nothing is removed.</li>
 *   <li>v3: persisted {@code bloodlines} list, seeded from legacy Mangekyo / Eternal fields. Nothing is removed.</li>
 * </ul>
 * Running a migration twice changes nothing the second time. Files newer than {@link #CURRENT} are never touched.
 */
public final class SchemaMigrator {
    public static final int CURRENT = 5;
    public static final String KEY_VERSION = "schema-version";

    /** Minimal key/value view over a profile document (adapter for YamlConfiguration in ProfileManager). */
    public interface Doc {
        String getString(String key);
        boolean getBoolean(String key, boolean def);
        int getInt(String key, int def);
        List<String> getStringList(String key);
        void set(String key, Object value);
    }

    private SchemaMigrator() {}

    public static int versionOf(Doc c) {
        return c.getInt(KEY_VERSION, 1);
    }

    /** Applies all pending migrations in place. Returns true if the document was modified. */
    public static boolean migrate(Doc c) {
        int v = versionOf(c);
        if (v >= CURRENT) return false;
        if (v < 2) toV2(c);
        if (v < 3) toV3(c);
        if (v < 4) toV4(c);
        if (v < 5) toV5(c);
        c.set(KEY_VERSION, CURRENT);
        return true;
    }

    private static void toV5(Doc c) {
        List<String> powers = new ArrayList<>(c.getStringList("mangekyo-powers"));
        String mk = c.getString("mangekyo");
        if (mk != null && !mk.isBlank() && !powers.contains(mk)) powers.add(mk);
        c.set("mangekyo-powers", powers);
    }

    private static void toV4(Doc c) {
        c.set("forced-bloodlines", new ArrayList<>(c.getStringList("forced-bloodlines")));
        c.set("forced-abilities", new ArrayList<>(c.getStringList("forced-abilities")));
    }

    private static void toV3(Doc c) {
        List<String> list = new ArrayList<>(c.getStringList("bloodlines"));
        String clan = c.getString("clan");
        if ("UCHIHA".equalsIgnoreCase(clan)) {
            String mk = c.getString("mangekyo");
            if (mk != null && !mk.isBlank() && !list.contains("mangekyo_sharingan")) list.add("mangekyo_sharingan");
            if (c.getBoolean("eternal", false) && !list.contains("eternal_mangekyo")) list.add("eternal_mangekyo");
        }
        c.set("bloodlines", list);
    }

    private static void toV2(Doc c) {
        String clan = c.getString("clan");
        if (clan != null && clan.trim().toUpperCase(Locale.ROOT).equals(Clan.LEGACY_HATAKE)) {
            if (c.getString("legacy-clan") == null) c.set("legacy-clan", Clan.LEGACY_HATAKE);
            c.set("clan", Clan.SENJU.name());
            List<String> hist = new ArrayList<>(c.getStringList("clan-history"));
            String entry = "HATAKE->SENJU | " + Instant.now() + " | schema-2 migration";
            if (hist.stream().noneMatch(h -> h.startsWith("HATAKE->SENJU"))) hist.add(entry);
            c.set("clan-history", hist);
        }
    }
}
