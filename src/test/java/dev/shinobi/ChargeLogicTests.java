package dev.shinobi;

/** Plain-Java tests for the pure charge rules (no Bukkit, no JUnit). Run: gradle logicTests. Exit code 1 on failure. */
public final class ChargeLogicTests {
    private static int run, failed;

    private static void check(String name, boolean ok) {
        run++;
        if (!ok) {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

    private static ChargeSpec spec() {
        return ChargeSpec.builder("lightning", 30, 70).stages(3).holdLimit(60).grace(6)
                .damageScale(0.7, 1.5).rangeScale(0.8, 1.3).build();
    }

    public static void main(String[] args) {
        ChargeSpec s = spec();

        // --- stage progression ---
        check("stage 0 at start", s.stageOf(0) == 0);
        check("stage 0 just below min", s.stageOf(29) == 0);
        check("stage 1 at min", s.stageOf(30) == 1);
        check("stage 2 midway", s.stageOf(60) == 2);
        check("stage 2 just below max", s.stageOf(69) == 2);
        check("full stage at max", s.stageOf(70) == 3);
        check("full stage stays full", s.stageOf(500) == 3);
        check("ready at min", !s.ready(29) && s.ready(30));
        check("full at max", !s.full(69) && s.full(70));
        int last = -1; boolean monotonic = true;
        for (int t = 0; t <= 200; t++) { int st = s.stageOf(t); if (st < last) monotonic = false; last = st; }
        check("stages never go backwards", monotonic);

        // --- scaling ---
        check("damage scale at min", near(s.damageScale(30), 0.7));
        check("damage scale below min uses min", near(s.damageScale(0), 0.7));
        check("damage scale at full", near(s.damageScale(70), 1.5));
        check("damage scale capped past full", near(s.damageScale(9999), 1.5));
        check("damage scale midway", near(s.damageScale(50), 1.1));
        check("range scale endpoints", near(s.rangeScale(30), 0.8) && near(s.rangeScale(70), 1.3));
        check("fraction bounds", near(s.fraction(-5), 0) && near(s.fraction(70), 1) && near(s.fraction(35), 0.5));

        // --- release decisions ---
        check("release inside grace is ignored", s.decideRelease(3) == ChargeSpec.Decision.IGNORE);
        check("early release fizzles", s.decideRelease(10) == ChargeSpec.Decision.FIZZLE);
        check("release at min casts", s.decideRelease(30) == ChargeSpec.Decision.RELEASE);
        check("release at full casts", s.decideRelease(70) == ChargeSpec.Decision.RELEASE);
        ChargeSpec ign = s.toBuilder().earlyRelease(ChargeSpec.EarlyRelease.IGNORE).build();
        check("IGNORE mode ignores early release", ign.decideRelease(10) == ChargeSpec.Decision.IGNORE);
        check("IGNORE mode still casts after min", ign.decideRelease(40) == ChargeSpec.Decision.RELEASE);

        // --- charges cannot be held forever ---
        check("holds below limit continue", s.onTick(70 + 59, true) == ChargeSpec.Tick.CONTINUE);
        check("overcharge auto-releases", s.onTick(70 + 60, true) == ChargeSpec.Tick.AUTO_RELEASE);
        check("overcharge can auto-cancel", s.onTick(70 + 60, false) == ChargeSpec.Tick.AUTO_CANCEL);

        // --- spec sanitising ---
        ChargeSpec bad = ChargeSpec.builder("x", 0, 0).stages(99).holdLimit(-5).grace(500).slowness(99)
                .damageScale(-1, -2).rangeScale(Double.NaN, 1).build();
        check("min clamped to 1", bad.minTicks == 1);
        check("max forced above min", bad.maxTicks > bad.minTicks);
        check("stages clamped", bad.stages == 6);
        check("hold limit has a floor", bad.holdLimitTicks >= 20);
        check("grace cannot exceed min", bad.graceTicks <= bad.minTicks);
        check("slowness clamped", bad.slownessAmp == 4);
        check("scales never negative", bad.minScale == 0 && bad.maxScale >= bad.minScale && bad.rangeMinScale == 0);
        ChargeSpec tweaked = s.toBuilder().minTicks(10).maxTicks(20).build();
        check("toBuilder keeps other fields", tweaked.stages == 3 && near(tweaked.maxScale, 1.5) && tweaked.minTicks == 10);

        // --- ledger: one settlement only ---
        ChargeLedger l = new ChargeLedger(30);
        check("ledger open at start", l.isOpen() && near(l.reserved(), 30));
        check("commit settles once", l.commit() && !l.isOpen());
        check("second commit is a no-op", !l.commit());
        check("refund after commit gives nothing", near(l.refund(1.0), 0));
        ChargeLedger r = new ChargeLedger(40);
        check("half refund", near(r.refund(0.5), 20));
        check("double refund gives nothing", near(r.refund(1.0), 0));
        check("commit after refund is a no-op", !r.commit());
        check("refund fraction clamped high", near(new ChargeLedger(10).refund(5), 10));
        check("refund fraction clamped low", near(new ChargeLedger(10).refund(-1), 0));
        check("NaN fraction refunds nothing", near(new ChargeLedger(10).refund(Double.NaN), 0));
        check("negative reservation becomes zero", near(new ChargeLedger(-9).reserved(), 0));

        // --- end-to-end accounting simulation: reserve, then exactly one settlement ---
        double chakra = 100, cost = 30;
        ChargeLedger a = new ChargeLedger(cost); chakra -= cost;
        if (s.decideRelease(40) == ChargeSpec.Decision.RELEASE) a.commit();
        chakra += a.refund(1.0); // late "cancel" after release must not give chakra back
        check("release spends chakra exactly once", near(chakra, 70));
        chakra = 100; ChargeLedger b = new ChargeLedger(cost); chakra -= cost;
        if (s.decideRelease(10) == ChargeSpec.Decision.FIZZLE) chakra += b.refund(0.5);
        chakra += b.refund(0.5); // duplicate cleanup (e.g. quit event after fizzle) must not refund twice
        check("fizzle refunds exactly once", near(chakra, 85));

        System.out.println(run + " checks, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
