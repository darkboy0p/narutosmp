package dev.shinobi;

import java.util.HashSet;
import java.util.Set;

/** Tests the pure headgear visibility and model-id rules. */
public final class HeadgearLogicTests {
    private static int run, failed;

    private static void check(String name, boolean ok) {
        run++;
        if (!ok) { failed++; System.out.println("FAIL: " + name); }
    }

    public static void main(String[] args) {
        check("upright player shows headgear", HeadgearRules.visible(false, false, false, false, false, false));
        check("swimming hides", !HeadgearRules.visible(true, false, false, false, false, false));
        check("gliding hides", !HeadgearRules.visible(false, true, false, false, false, false));
        check("sleeping hides", !HeadgearRules.visible(false, false, true, false, false, false));
        check("dead hides", !HeadgearRules.visible(false, false, false, true, false, false));
        check("spectator hides", !HeadgearRules.visible(false, false, false, false, true, false));
        check("invisible hides", !HeadgearRules.visible(false, false, false, false, false, true));
        int hidden = 0;
        for (int m = 1; m < 64; m++) {
            if (!HeadgearRules.visible((m & 1) != 0, (m & 2) != 0, (m & 4) != 0, (m & 8) != 0, (m & 16) != 0, (m & 32) != 0)) hidden++;
        }
        check("any single bad flag hides (63 combinations)", hidden == 63);

        Set<String> ids = new HashSet<>();
        for (String k : EyeStyle.ALL) ids.add(HeadgearRules.modelId(k));
        check("one unique model id per eye style", ids.size() == EyeStyle.ALL.size());
        check("plain headband model id", HeadgearRules.modelId("none").equals("headgear_none"));
        check("style model id", HeadgearRules.modelId("sharingan_3").equals("headgear_sharingan_3"));

        System.out.println(run + " checks, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
