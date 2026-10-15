package dev.shinobi;

import java.util.*;
import dev.shinobi.DojutsuProgression.*;

public final class DojutsuProgressionTests {
    private static int run, failed;
    private static void check(String n, boolean ok) { run++; if (!ok) { failed++; System.out.println("FAIL: " + n); } }

    public static void main(String[] args) {
        Map<Stage, Req> reqs = new EnumMap<>(Stage.class);
        reqs.put(Stage.ONE, new Req(0, 0, 0, true));
        reqs.put(Stage.TWO, new Req(100, 2, 1, false));
        reqs.put(Stage.THREE, new Req(300, 6, 2, false));
        reqs.put(Stage.MANGEKYO, new Req(300, 6, 3, true));
        reqs.put(Stage.ETERNAL, new Req(600, 12, 4, false));
        Set<Stage> on = EnumSet.of(Stage.ONE, Stage.TWO, Stage.THREE, Stage.MANGEKYO, Stage.ETERNAL);
        Status rich = new Status(9999, 99, 9, true), poor = new Status(0, 0, 0, false);

        check("reqs validate", DojutsuProgression.validate(reqs).isEmpty());
        Map<Stage, Req> bad = new EnumMap<>(reqs); bad.put(Stage.THREE, new Req(50, 6, 2, false));
        check("decreasing reqs rejected", !DojutsuProgression.validate(bad).isEmpty());
        Map<Stage, Req> neg = new EnumMap<>(Stage.class); neg.put(Stage.ONE, new Req(-1, 0, 0, false));
        check("negative reqs rejected", !DojutsuProgression.validate(neg).isEmpty());

        check("dormant->one with challenge", DojutsuProgression.canAdvance(Stage.DORMANT, Stage.ONE, on, reqs, rich, true).ok());
        check("dormant->one blocked without challenge", !DojutsuProgression.canAdvance(Stage.DORMANT, Stage.ONE, on, reqs, new Status(9999, 99, 9, false), true).ok());
        check("one->two ok", DojutsuProgression.canAdvance(Stage.ONE, Stage.TWO, on, reqs, rich, true).ok());
        check("one->two blocked: progress", !DojutsuProgression.canAdvance(Stage.ONE, Stage.TWO, on, reqs, new Status(99, 9, 9, true), true).ok());
        check("one->two blocked: missions", !DojutsuProgression.canAdvance(Stage.ONE, Stage.TWO, on, reqs, new Status(999, 1, 9, true), true).ok());
        check("one->two blocked: grade", !DojutsuProgression.canAdvance(Stage.ONE, Stage.TWO, on, reqs, new Status(999, 9, 0, true), true).ok());
        check("two->three ok", DojutsuProgression.canAdvance(Stage.TWO, Stage.THREE, on, reqs, rich, true).ok());
        check("skip one->three rejected", !DojutsuProgression.canAdvance(Stage.ONE, Stage.THREE, on, reqs, rich, true).ok());
        check("skip dormant->mangekyo rejected", !DojutsuProgression.canAdvance(Stage.DORMANT, Stage.MANGEKYO, on, reqs, rich, true).ok());
        check("repeat same stage rejected", !DojutsuProgression.canAdvance(Stage.TWO, Stage.TWO, on, reqs, rich, true).ok());
        check("backwards rejected", !DojutsuProgression.canAdvance(Stage.THREE, Stage.TWO, on, reqs, rich, true).ok());
        check("non-uchiha rejected", !DojutsuProgression.canAdvance(Stage.DORMANT, Stage.ONE, on, reqs, rich, false).ok());
        check("poor never advances", !DojutsuProgression.canAdvance(Stage.TWO, Stage.THREE, on, reqs, poor, true).ok());
        check("rinnegan disabled by default", !DojutsuProgression.canAdvance(Stage.ETERNAL, Stage.RINNEGAN, on, reqs, rich, true).ok());
        Set<Stage> withR = EnumSet.copyOf(on); withR.add(Stage.RINNEGAN);
        check("rinnegan still needs config reqs", !DojutsuProgression.canAdvance(Stage.ETERNAL, Stage.RINNEGAN, withR, reqs, rich, true).ok());
        check("rinnegan last has no next", Stage.RINNEGAN.next() == null);
        check("disabled mangekyo rejected", !DojutsuProgression.canAdvance(Stage.THREE, Stage.MANGEKYO, EnumSet.of(Stage.THREE), reqs, rich, true).ok());
        check("stage parse garbage -> dormant", Stage.parse("nonsense") == Stage.DORMANT && Stage.parse(null) == Stage.DORMANT);

        // anti-farm
        check("credit normal", DojutsuProgression.credit(10, false, true, 10_000, 0, 1000, 0, 5) == 10);
        check("credit self-target zero", DojutsuProgression.credit(10, true, true, 10_000, 0, 1000, 0, 5) == 0);
        check("credit invalid target zero", DojutsuProgression.credit(10, false, false, 10_000, 0, 1000, 0, 5) == 0);
        check("credit cooldown zero", DojutsuProgression.credit(10, false, true, 10_500, 10_000, 1000, 0, 5) == 0);
        check("credit diminishes", DojutsuProgression.credit(10, false, true, 10_000, 0, 1000, 2, 5) == 6);
        check("credit cap zero", DojutsuProgression.credit(10, false, true, 10_000, 0, 1000, 5, 5) == 0);
        check("credit never negative", DojutsuProgression.credit(10, false, true, 10_000, 0, 1000, 99, 5) == 0);
        int total = 0, hits = 0; long last = -10_000;
        for (int i = 0; i < 1000; i++) { long now = i * 1000L; int c = DojutsuProgression.credit(10, false, true, now, last, 1000, hits, 5); if (c > 0) { total += c; hits++; last = now; } }
        check("loop farming bounded", total <= 10 * 5);

        // lifecycle
        Awakening a = Awakening.DORMANT;
        check("begin from dormant refused", !DojutsuProgression.allowed(a, Event.BEGIN));
        a = DojutsuProgression.apply(a, Event.BECOME_ELIGIBLE); check("eligible", a == Awakening.ELIGIBLE);
        check("complete from eligible refused", DojutsuProgression.apply(a, Event.COMPLETE) == Awakening.ELIGIBLE);
        a = DojutsuProgression.apply(a, Event.BEGIN); check("in progress", a == Awakening.IN_PROGRESS);
        check("duplicate begin refused", DojutsuProgression.apply(a, Event.BEGIN) == Awakening.IN_PROGRESS);
        check("interrupt returns to eligible", DojutsuProgression.apply(a, Event.INTERRUPT) == Awakening.ELIGIBLE);
        check("recover on load never awakens", DojutsuProgression.recoverOnLoad(Awakening.IN_PROGRESS) == Awakening.ELIGIBLE);
        check("recover keeps awakened", DojutsuProgression.recoverOnLoad(Awakening.AWAKENED) == Awakening.AWAKENED);
        a = DojutsuProgression.apply(a, Event.COMPLETE); check("awakened", a == Awakening.AWAKENED);
        check("complete twice no change", DojutsuProgression.apply(a, Event.COMPLETE) == Awakening.AWAKENED);
        check("interrupt after awakened no change", DojutsuProgression.apply(a, Event.INTERRUPT) == Awakening.AWAKENED);
        check("admin reset", DojutsuProgression.apply(a, Event.ADMIN_RESET) == Awakening.DORMANT);
        check("awakening parse garbage", Awakening.parse("x") == Awakening.DORMANT);

        // mangekyo
        check("mk ok", DojutsuProgression.canBeginMangekyo(true, Stage.THREE, Awakening.ELIGIBLE, true, true).ok());
        check("mk needs uchiha", !DojutsuProgression.canBeginMangekyo(false, Stage.THREE, Awakening.ELIGIBLE, true, true).ok());
        check("mk needs three tomoe", !DojutsuProgression.canBeginMangekyo(true, Stage.TWO, Awakening.ELIGIBLE, true, true).ok());
        check("mk needs milestone", !DojutsuProgression.canBeginMangekyo(true, Stage.THREE, Awakening.ELIGIBLE, true, false).ok());
        check("mk disabled", !DojutsuProgression.canBeginMangekyo(true, Stage.THREE, Awakening.ELIGIBLE, false, true).ok());
        check("mk duplicate in progress", !DojutsuProgression.canBeginMangekyo(true, Stage.THREE, Awakening.IN_PROGRESS, true, true).ok());
        check("mk already awakened", !DojutsuProgression.canBeginMangekyo(true, Stage.THREE, Awakening.AWAKENED, true, true).ok());
        check("mk dormant awakening blocked", !DojutsuProgression.canBeginMangekyo(true, Stage.THREE, Awakening.DORMANT, true, true).ok());

        // assignment
        List<String> pool = List.of("mangekyo_kamui", "mangekyo_amaterasu", "mangekyo_tsukuyomi");
        check("assign picks by roll", "mangekyo_kamui".equals(DojutsuProgression.assign(pool, Set.of(), List.of(), 0.0))
                && "mangekyo_tsukuyomi".equals(DojutsuProgression.assign(pool, Set.of(), List.of(), 0.99)));
        check("assign roll 1.0 stays in range", DojutsuProgression.assign(pool, Set.of(), List.of(), 1.0) != null);
        check("assign skips disabled", "mangekyo_tsukuyomi".equals(DojutsuProgression.assign(pool, Set.of("mangekyo_kamui", "mangekyo_amaterasu"), List.of(), 0.0)));
        check("assign all disabled -> null", DojutsuProgression.assign(pool, Set.copyOf(pool), List.of(), 0.5) == null);
        check("assign never rerolls", "mangekyo_amaterasu".equals(DojutsuProgression.assign(pool, Set.of(), List.of("mangekyo_amaterasu"), 0.0)));
        check("empty pool -> null", DojutsuProgression.assign(List.of(), Set.of(), List.of(), 0.5) == null);
        check("reassign needs authority", !DojutsuProgression.canReassign(false, false, true).ok());
        check("admin may reassign", DojutsuProgression.canReassign(true, false, true).ok());
        check("server reroll rule allows", DojutsuProgression.canReassign(false, true, true).ok());
        check("first assignment always ok", DojutsuProgression.canReassign(false, false, false).ok());

        // eternal
        boolean[] conf = {true, true, false}, done1 = {true, false, false}, done2 = {true, true, false};
        check("eternal any-route ok", DojutsuProgression.canEvolveEternal(Stage.MANGEKYO, Awakening.AWAKENED, true, true, done1, conf).ok());
        check("eternal all-routes needs all", !DojutsuProgression.canEvolveEternal(Stage.MANGEKYO, Awakening.AWAKENED, true, false, done1, conf).ok());
        check("eternal all-routes ok", DojutsuProgression.canEvolveEternal(Stage.MANGEKYO, Awakening.AWAKENED, true, false, done2, conf).ok());
        check("eternal unconfigured routes cannot be done", !DojutsuProgression.canEvolveEternal(Stage.MANGEKYO, Awakening.AWAKENED, true, true, new boolean[]{false, false, true}, conf).ok());
        check("eternal no routes configured", !DojutsuProgression.canEvolveEternal(Stage.MANGEKYO, Awakening.AWAKENED, true, true, done1, new boolean[]{false, false, false}).ok());
        check("eternal disabled", !DojutsuProgression.canEvolveEternal(Stage.MANGEKYO, Awakening.AWAKENED, false, true, done2, conf).ok());
        check("eternal needs mangekyo", !DojutsuProgression.canEvolveEternal(Stage.THREE, Awakening.AWAKENED, true, true, done2, conf).ok());
        check("eternal needs awakened", !DojutsuProgression.canEvolveEternal(Stage.MANGEKYO, Awakening.IN_PROGRESS, true, true, done2, conf).ok());

        System.out.println(run + " dojutsu progression checks, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
