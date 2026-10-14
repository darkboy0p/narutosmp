package dev.shinobi;

import java.util.ArrayList;
import java.util.List;
import dev.shinobi.GradeRules.*;

/** Tests the pure SMP grade rules: validation, promotion, no skipping, approval gates, disabled rungs, admin targets. */
public final class GradeLogicTests {
    private static int run, failed;
    private static void check(String n, boolean ok) { run++; if (!ok) { failed++; System.out.println("FAIL: " + n); } }

    public static void main(String[] args) {
        List<Grade> d = GradeRules.defaults();
        check("defaults validate", GradeRules.validate(d).isEmpty());
        check("starting is academy", GradeRules.starting(d).equals("academy"));
        check("empty ladder rejected", !GradeRules.validate(List.of()).isEmpty());
        check("null ladder rejected", !GradeRules.validate(null).isEmpty());

        List<Grade> dup = new ArrayList<>(d); dup.add(new Grade("genin", "X", true, 9999, 999, 99, false));
        check("duplicate id rejected", !GradeRules.validate(dup).isEmpty());
        check("bad id rejected", !GradeRules.validate(List.of(new Grade("Bad Id", "x", true, 0, 0, 0, false))).isEmpty());
        check("negative threshold rejected", !GradeRules.validate(List.of(new Grade("a", "A", true, -1, 0, 0, false))).isEmpty());
        List<Grade> dec = List.of(new Grade("a", "A", true, 100, 0, 0, false), new Grade("b", "B", true, 50, 0, 0, false));
        check("decreasing requirements rejected", !GradeRules.validate(dec).isEmpty());
        check("all disabled rejected", !GradeRules.validate(List.of(new Grade("a", "A", false, 0, 0, 0, false))).isEmpty());

        Stats none = new Stats(0, 0, 0, false);
        check("cannot promote with nothing", !GradeRules.canPromote(d, "academy", none).ok());
        check("promote when all met", GradeRules.canPromote(d, "academy", new Stats(100, 3, 1, false)).ok());
        check("xp short blocks", !GradeRules.canPromote(d, "academy", new Stats(99, 3, 1, false)).ok());
        check("missions short blocks", !GradeRules.canPromote(d, "academy", new Stats(100, 2, 1, false)).ok());
        check("training short blocks", !GradeRules.canPromote(d, "academy", new Stats(100, 3, 0, false)).ok());
        check("huge stats still only reach NEXT rung (no skipping)", GradeRules.canPromote(d, "academy", new Stats(99999, 999, 99, false)).ok()
                && GradeRules.indexOf(d, "genin") == 1);
        check("approval gate blocks without approval", !GradeRules.canPromote(d, "jonin", new Stats(99999, 999, 99, false)).ok());
        check("approval gate passes with approval", GradeRules.canPromote(d, "jonin", new Stats(99999, 999, 99, true)).ok());
        check("top grade cannot promote", !GradeRules.canPromote(d, "kage", new Stats(99999, 999, 99, true)).ok());
        check("unknown current grade blocks", !GradeRules.canPromote(d, "hokage", new Stats(99999, 999, 99, true)).ok());

        List<Grade> skip = new ArrayList<>(d);
        skip.set(1, new Grade("genin", "Genin", false, 100, 3, 1, false));
        check("disabled rung skipped in ladder", GradeRules.enabled(skip).size() == 6 && GradeRules.indexOf(skip, "genin") == -1);
        check("promote from academy goes to chunin reqs", !GradeRules.canPromote(skip, "academy", new Stats(100, 3, 1, false)).ok()
                && GradeRules.canPromote(skip, "academy", new Stats(500, 10, 3, false)).ok());
        check("player on now-disabled grade cannot self-promote", !GradeRules.canPromote(skip, "genin", new Stats(99999, 999, 99, true)).ok());

        check("progress 0 at start", GradeRules.progress(d, "academy", none) == 0);
        check("progress is lowest ratio", Math.abs(GradeRules.progress(d, "academy", new Stats(100, 1, 1, false)) - 1.0 / 3) < 1e-9);
        check("progress capped at 1", GradeRules.progress(d, "academy", new Stats(9999, 99, 9, false)) == 1);
        check("progress 1 at top", GradeRules.progress(d, "kage", none) == 1);

        check("admin set valid", GradeRules.canSet(d, "anbu").ok());
        check("admin set unknown rejected", !GradeRules.canSet(d, "hokage").ok());
        check("admin set disabled rejected", !GradeRules.canSet(skip, "genin").ok());
        check("demote from chunin gives genin", "genin".equals(GradeRules.demoteTarget(d, "chunin")));
        check("demote at bottom is null", GradeRules.demoteTarget(d, "academy") == null);
        check("demote unknown is null", GradeRules.demoteTarget(d, "nope") == null);

        System.out.println(run + " grade checks, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
