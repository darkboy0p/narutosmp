package dev.shinobi;

import java.util.HashSet;
import java.util.Set;

/** Plain-Java tests for the pure bloodline registry and eligibility rules. */
public final class BloodlineLogicTests {
    private static int run, failed;

    private static void check(String name, boolean ok) {
        run++;
        if (!ok) { failed++; System.out.println("FAIL: " + name); }
    }

    private static BloodlineRules.Result r(BloodlineRegistry reg, String id, Clan c, boolean awakened, String... unlocked) {
        return BloodlineRules.check(reg, id, c, awakened, new HashSet<>(Set.of(unlocked)));
    }

    public static void main(String[] args) {
        BloodlineRegistry reg = BloodlineRegistry.defaults();
        check("default registry validates clean", reg.validate().isEmpty());
        check("all 13 rare bloodlines + 9 core/optional defined", reg.all().size() == 22);

        // --- the five core clan identities ---
        String[][] core = {{"sharingan", "UCHIHA"}, {"byakugan", "HYUGA"}, {"wood_release", "SENJU"},
                {"sealing_arts", "UZUMAKI"}, {"shadow_manipulation", "NARA"}};
        for (String[] c : core) {
            Clan own = Clan.valueOf(c[1]);
            check(c[0] + " ok for " + own, r(reg, c[0], own, true) == BloodlineRules.Result.OK);
            check(c[0] + " needs awakening", r(reg, c[0], own, false) == BloodlineRules.Result.NOT_AWAKENED);
            check(c[0] + " denied without clan", r(reg, c[0], null, true) == BloodlineRules.Result.WRONG_CLAN);
            for (Clan other : Clan.values()) {
                if (other != own) check(c[0] + " denied for " + other, r(reg, c[0], other, true) == BloodlineRules.Result.WRONG_CLAN);
            }
        }

        // --- Uchiha evolution chain needs each earlier stage ---
        check("mangekyo needs unlock", r(reg, "mangekyo_sharingan", Clan.UCHIHA, true) == BloodlineRules.Result.NOT_UNLOCKED);
        check("mangekyo ok when unlocked", r(reg, "mangekyo_sharingan", Clan.UCHIHA, true, "mangekyo_sharingan") == BloodlineRules.Result.OK);
        check("eternal needs mangekyo first", r(reg, "eternal_mangekyo", Clan.UCHIHA, true, "eternal_mangekyo") == BloodlineRules.Result.MISSING_PARENT);
        check("eternal ok with chain", r(reg, "eternal_mangekyo", Clan.UCHIHA, true, "mangekyo_sharingan", "eternal_mangekyo") == BloodlineRules.Result.OK);
        check("mangekyo denied for Hyuga even if 'unlocked'", r(reg, "mangekyo_sharingan", Clan.HYUGA, true, "mangekyo_sharingan") == BloodlineRules.Result.WRONG_CLAN);

        // --- optional evolutions are off by default ---
        check("rinnegan disabled by default", r(reg, "rinnegan", Clan.UCHIHA, true, "mangekyo_sharingan", "eternal_mangekyo", "rinnegan") == BloodlineRules.Result.DISABLED);
        check("tenseigan disabled by default", r(reg, "tenseigan", Clan.HYUGA, true, "tenseigan") == BloodlineRules.Result.DISABLED);
        BloodlineRegistry on = BloodlineRegistry.defaults();
        on.register(on.get("tenseigan").toBuilder().enabled(true).build());
        check("tenseigan works once enabled", BloodlineRules.check(on, "tenseigan", Clan.HYUGA, true, Set.of("tenseigan")) == BloodlineRules.Result.OK);
        check("tenseigan needs byakugan (innate) => ok only for awakened Hyuga", BloodlineRules.check(on, "tenseigan", Clan.HYUGA, false, Set.of("tenseigan")) == BloodlineRules.Result.NOT_AWAKENED);

        // --- rare bloodlines: disabled, never for all five clans ---
        int rareCount = 0;
        for (BloodlineDef d : reg.all()) {
            if (d.kind != BloodlineDef.Kind.RARE || d.parent != null) continue;
            if (d.id.equals("rinnegan") || d.id.equals("tenseigan")) continue;
            rareCount++;
            check(d.id + " disabled by default", !d.enabled);
            check(d.id + " not for all five clans", d.eligibleClans.size() < Clan.values().length && !d.anyClan);
            check(d.id + " is not innate", !d.innate);
            for (Clan c : Clan.values()) check(d.id + " blocked for " + c + " by default", r(reg, d.id, c, true, d.id) == BloodlineRules.Result.DISABLED);
        }
        check("13 rare bloodlines", rareCount == 13);

        // --- enabling a rare bloodline: clan list + unlock still required ---
        BloodlineRegistry ice = BloodlineRegistry.defaults();
        ice.register(ice.get("ice_release").toBuilder().enabled(true).clans(Clan.UZUMAKI, Clan.SENJU).build());
        check("ice ok for eligible unlocked clan", BloodlineRules.check(ice, "ice_release", Clan.SENJU, true, Set.of("ice_release")) == BloodlineRules.Result.OK);
        check("ice needs unlock", BloodlineRules.check(ice, "ice_release", Clan.SENJU, true, Set.of()) == BloodlineRules.Result.NOT_UNLOCKED);
        check("ice denied for other clan despite unlock", BloodlineRules.check(ice, "ice_release", Clan.NARA, true, Set.of("ice_release")) == BloodlineRules.Result.WRONG_CLAN);
        check("ice denied without clan", BloodlineRules.check(ice, "ice_release", null, true, Set.of("ice_release")) == BloodlineRules.Result.WRONG_CLAN);

        // --- owner exception: any-clan ---
        BloodlineRegistry bone = BloodlineRegistry.defaults();
        check("bone has no clans by default", bone.get("shikotsumyaku").eligibleClans.isEmpty());
        bone.register(bone.get("shikotsumyaku").toBuilder().enabled(true).anyClan(true).build());
        check("any-clan exception works", BloodlineRules.check(bone, "shikotsumyaku", Clan.NARA, true, Set.of("shikotsumyaku")) == BloodlineRules.Result.OK);
        check("any-clan still needs unlock", BloodlineRules.check(bone, "shikotsumyaku", Clan.NARA, true, Set.of()) == BloodlineRules.Result.NOT_UNLOCKED);
        check("any-clan still needs awakening", BloodlineRules.check(bone, "shikotsumyaku", Clan.NARA, false, Set.of("shikotsumyaku")) == BloodlineRules.Result.NOT_AWAKENED);

        // --- validation catches bad config ---
        BloodlineRegistry bad = BloodlineRegistry.defaults();
        bad.register(bad.get("mangekyo_sharingan").toBuilder().parent("nope").build());
        check("unknown parent reported", bad.validate().stream().anyMatch(s -> s.contains("unknown parent")));
        BloodlineRegistry loop = BloodlineRegistry.defaults();
        loop.register(loop.get("sharingan").toBuilder().innate(false).parent("mangekyo_sharingan").build());
        check("parent loop reported", loop.validate().stream().anyMatch(s -> s.contains("loops")));
        check("loop never grants access", BloodlineRules.check(loop, "sharingan", Clan.UCHIHA, true, Set.of("sharingan", "mangekyo_sharingan")) != BloodlineRules.Result.OK);
        BloodlineRegistry orphan = BloodlineRegistry.defaults();
        orphan.register(orphan.get("ice_release").toBuilder().enabled(true).clans(Set.of()).build());
        check("enabled-but-unreachable reported", orphan.validate().stream().anyMatch(s -> s.contains("no clan")));

        // --- misc ---
        check("unknown id", r(reg, "does_not_exist", Clan.UCHIHA, true) == BloodlineRules.Result.UNKNOWN);
        check("null id", r(reg, null, Clan.UCHIHA, true) == BloodlineRules.Result.UNKNOWN);
        check("ids case-insensitive", reg.get("SHARINGAN") != null);
        check("describe covers every result", java.util.Arrays.stream(BloodlineRules.Result.values()).noneMatch(x -> BloodlineRules.describe(x).isEmpty()));

        System.out.println(run + " checks, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
