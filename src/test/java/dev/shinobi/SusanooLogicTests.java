package dev.shinobi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Susanoo + other abilities. The state machine and compatibility rules are real classes; the scenarios combine them with the
 * real charge/ledger classes in a small model of the engine's accounting (chakra pool + per-ability cooldown map). This tests
 * the LOGIC of independence; it does not run Bukkit, the engine or a server.
 */
public final class SusanooLogicTests {
    private static int run, failed;

    private static void check(String name, boolean ok) {
        run++;
        if (!ok) { failed++; System.out.println("FAIL: " + name); }
    }

    private static boolean near(double a, double b) { return Math.abs(a - b) < 1e-9; }

    /** Minimal model of what the engine does per player. */
    static final class Sim {
        double chakra = 100;
        final Map<String, Long> cooldowns = new HashMap<>();
        final SusanooState sus = new SusanooState();
        ChargeLedger ledger;       // active charge reservation, if any
        String chargingId;
        int chargeTicks;
        ChargeSpec spec;
        long now = 1_000_000L;

        boolean instantCast(String id, double cost, long cdMs, Set<String> incompatible) {
            if (AbilityCompat.blocked(sus.active(), id, incompatible)) return false;
            if (chargingId != null) return false;                       // engine: finish the current charge first
            Long cd = cooldowns.get(id);
            if (cd != null && cd > now) return false;
            if (chakra < cost) return false;
            chakra -= cost;
            cooldowns.put(id, now + cdMs);
            return true;
        }

        boolean startCharge(String id, double cost, long cdMs, ChargeSpec s, Set<String> incompatible) {
            if (AbilityCompat.blocked(sus.active(), id, incompatible) || chargingId != null) return false;
            Long cd = cooldowns.get(id);
            if (cd != null && cd > now) return false;
            if (chakra < cost) return false;
            chakra -= cost;
            ledger = new ChargeLedger(cost);
            chargingId = id; spec = s; chargeTicks = 0;
            pendingCd = cdMs;
            return true;
        }
        long pendingCd;

        void tickSecond(double drainPerStage) {
            now += 1000;
            if (chargingId != null) chargeTicks += 20;
            if (sus.active()) {
                SusanooState.Upkeep u = sus.upkeep(chakra, false, drainPerStage);
                chakra = u.chakra();
                if (u.collapsed()) sus.end();
            }
        }

        boolean release() {
            if (chargingId == null || spec.decideRelease(chargeTicks) != ChargeSpec.Decision.RELEASE) return false;
            ledger.commit();
            cooldowns.put(chargingId, now + pendingCd);
            chargingId = null; ledger = null;
            return true;
        }

        void cancelCharge(double refundFraction) {
            if (chargingId == null) return;
            chakra += ledger.refund(refundFraction);
            chargingId = null; ledger = null;
        }
    }

    public static void main(String[] args) {
        // ---------------- state machine ----------------
        SusanooState s = new SusanooState();
        check("starts inactive", !s.active() && s.stage() == 0);
        check("first cast starts", s.advance() == SusanooState.Advance.STARTED && s.stage() == 1);
        check("second cast advances", s.advance() == SusanooState.Advance.ADVANCED && s.stage() == 2);
        s.advance(); s.advance();
        check("reaches the complete stage", s.stage() == SusanooState.MAX_STAGE);
        check("cast at the last stage dismisses", s.advance() == SusanooState.Advance.DISMISSED && !s.active());
        check("restart after dismissal", s.advance() == SusanooState.Advance.STARTED);
        s.end();
        check("end() is idempotent", !s.active());
        s.end();
        check("end() twice is harmless", s.stage() == 0);

        // ---------------- upkeep: costs rise, never vanish ----------------
        double prev = -1;
        for (int st = 0; st <= SusanooState.MAX_STAGE; st++) {
            double d = SusanooState.drainPerSecond(st, 4.0);
            check("drain at stage " + st + " does not decrease", d >= prev);
            if (st > 0) check("drain at stage " + st + " is never free", d > 0);
            prev = d;
        }
        check("top stage drains the most", SusanooState.drainPerSecond(4, 4.0) > SusanooState.drainPerSecond(1, 4.0));
        check("negative config cannot create chakra", SusanooState.drainPerSecond(3, -5.0) == 0.0);
        check("resistance grows with stage", SusanooState.resistanceAmplifier(1) < SusanooState.resistanceAmplifier(4));
        check("resistance is capped (never invulnerable)", SusanooState.resistanceAmplifier(99) == 3 && SusanooState.resistanceAmplifier(0) == 0);
        check("eternal removes strain, not chakra drain", SusanooState.strainPerSecond(3, true) == 0.0 && SusanooState.strainPerSecond(3, false) == 3.0);
        SusanooState up = new SusanooState(); up.advance(); up.advance();
        SusanooState.Upkeep u1 = up.upkeep(100, false, 4.0);
        check("upkeep drains 8 at stage 2", near(u1.chakra(), 92) && !u1.collapsed());
        SusanooState.Upkeep u2 = up.upkeep(5, false, 4.0);
        check("chakra never negative and Susanoo collapses at zero", near(u2.chakra(), 0) && u2.collapsed());

        // ---------------- compatibility table ----------------
        Set<String> table = AbilityCompat.normalise(AbilityCompat.SUSANOO_DEFAULT);
        for (String ok : List.of("fireball", "great_fireball", "amaterasu", "tsukuyomi", "chidori", "rasengan", "rasenshuriken", "sharingan_dodge", "susanoo", "mangekyo_ability", "substitution"))
            check(ok + " is allowed during Susanoo by default", !AbilityCompat.blocked(true, ok, table));
        check("kamui is explicitly incompatible", AbilityCompat.blocked(true, "kamui", table));
        check("incompatible id match is case-insensitive", AbilityCompat.blocked(true, "KAMUI", table));
        check("nothing is blocked while Susanoo is off", !AbilityCompat.blocked(false, "kamui", table));
        check("empty table blocks nothing", !AbilityCompat.blocked(true, "kamui", Set.of()));
        check("default table is small, not 'everything'", table.size() == 1);
        check("normalise drops junk ids", AbilityCompat.normalise(List.of("kamui", "../x", "", "a b", "Chidori")).equals(Set.of("kamui", "chidori")));
        check("normalise tolerates null", AbilityCompat.normalise(null).isEmpty());
        check("null ability is never blocked", !AbilityCompat.blocked(true, null, table));

        // ---------------- scenarios: Susanoo + other abilities ----------------
        ChargeSpec rasengan = ChargeSpec.builder("rasengan", 20, 50).build();
        ChargeSpec fire = ChargeSpec.builder("fire", 15, 45).build();

        // A: instant ability inside Susanoo; cooldowns are independent
        Sim a = new Sim(); a.sus.advance(); a.sus.advance();
        check("A: Amaterasu casts inside Susanoo", a.instantCast("amaterasu", 50, 12_000, table));
        check("A: Susanoo still active", a.sus.active() && a.sus.stage() == 2);
        check("A: only Amaterasu got a cooldown", a.cooldowns.keySet().equals(Set.of("amaterasu")));
        check("A: Amaterasu cost its normal 50", near(a.chakra, 50));
        check("A: Amaterasu is now on cooldown", !a.instantCast("amaterasu", 50, 12_000, table));
        check("A: advancing Susanoo is still possible and uses its own cooldown key", a.instantCast("susanoo", 50, 12_000, table) && a.cooldowns.keySet().equals(Set.of("amaterasu", "susanoo")));
        Sim a2 = new Sim(); a2.sus.advance();
        a2.cooldowns.put("susanoo", a2.now + 12_000);                     // Susanoo on cooldown...
        check("A2: Susanoo cooldown does not block Amaterasu", a2.instantCast("amaterasu", 50, 12_000, table));
        Sim a3 = new Sim(); a3.sus.advance();
        a3.cooldowns.put("amaterasu", a3.now + 12_000);                   // ...and the reverse
        check("A3: Amaterasu cooldown does not block Susanoo advancing", a3.instantCast("susanoo", 50, 12_000, table));

        // B: charge + release a technique during Susanoo
        Sim b = new Sim(); b.sus.advance(); b.sus.advance(); b.sus.advance();
        double before = b.chakra;
        check("B: Rasengan charge starts inside Susanoo", b.startCharge("rasengan", 40, 10_000, rasengan, table));
        check("B: reservation takes exactly the ability cost", near(b.chakra, before - 40) && near(b.ledger.reserved(), 40));
        for (int i = 0; i < 2; i++) b.tickSecond(4.0);
        check("B: Susanoo kept draining its own upkeep (3 stages x 4 x 2s = 24)", near(b.chakra, before - 40 - 24));
        check("B: Susanoo did not cancel the charge", b.chargingId != null && b.ledger.isOpen());
        check("B: Susanoo still active while charging", b.sus.active() && b.sus.stage() == 3);
        check("B: cannot cast another ability mid-charge", !b.instantCast("fireball", 20, 5000, table));
        check("B: release resolves once", b.release());
        check("B: second release does nothing", !b.release());
        check("B: Rasengan got its own cooldown", b.cooldowns.keySet().equals(Set.of("rasengan")));
        check("B: no refund after a committed release", near(b.chakra, before - 40 - 24));
        check("B: Susanoo untouched by the release", b.sus.active());

        // C: cancel a charge inside Susanoo: refund only the ability's reservation
        Sim c = new Sim(); c.sus.advance();
        c.startCharge("great_fireball", 35, 8000, fire, table);
        c.tickSecond(4.0);
        double mid = c.chakra;
        c.cancelCharge(0.5);
        check("C: cancel refunds half of the reservation only", near(c.chakra, mid + 17.5));
        check("C: Susanoo unaffected by the cancel", c.sus.active() && c.sus.stage() == 1);
        c.cancelCharge(1.0);
        check("C: second cancel refunds nothing", near(c.chakra, mid + 17.5));

        // D: incompatible ability is refused, nothing is charged
        Sim d = new Sim(); d.sus.advance();
        double dBefore = d.chakra;
        check("D: Kamui refused while Susanoo is active", !d.instantCast("kamui", 35, 12_000, table));
        check("D: refusal charges nothing and sets no cooldown", near(d.chakra, dBefore) && d.cooldowns.isEmpty());
        d.sus.end();
        check("D: Kamui works again once Susanoo ends", d.instantCast("kamui", 35, 12_000, table));

        // E: chakra exhaustion collapses Susanoo without touching the open charge or cooldowns
        Sim e = new Sim(); e.chakra = 60; e.sus.advance(); e.sus.advance(); e.sus.advance(); e.sus.advance();
        e.startCharge("rasengan", 40, 10_000, rasengan, table);     // 60 -> 20 left
        e.tickSecond(4.0);                                           // drain 16 -> 4
        e.tickSecond(4.0);                                           // drain 16 -> 0, collapse
        check("E: Susanoo collapsed on zero chakra", !e.sus.active() && near(e.chakra, 0));
        check("E: the open charge survived the collapse", e.chargingId != null && e.ledger.isOpen() && near(e.ledger.reserved(), 40));
        check("E: cooldowns untouched by the collapse", e.cooldowns.isEmpty());

        // F: higher stage never makes abilities cheaper or free
        for (int st = 1; st <= 4; st++) {
            Sim f = new Sim(); for (int i = 0; i < st; i++) f.sus.advance();
            f.chakra = 30;
            check("F" + st + ": a 40-chakra ability is still unaffordable at 30 chakra, stage " + st, !f.instantCast("amaterasu", 40, 1000, table));
            f.chakra = 40;
            check("F" + st + ": and costs the full 40 when affordable", f.instantCast("amaterasu", 40, 1000, table) && near(f.chakra, 0));
        }

        // G: death / logout cleanup ends Susanoo and force-cancels the charge without refund; nothing left over
        Sim g = new Sim(); g.sus.advance(); g.sus.advance();
        g.startCharge("rasengan", 40, 10_000, rasengan, table);
        double gChakra = g.chakra;
        g.sus.end(); g.cancelCharge(0.0);
        check("G: cleanup leaves no Susanoo and no charge", !g.sus.active() && g.chargingId == null && g.ledger == null);
        check("G: forced cleanup refunds nothing", near(g.chakra, gChakra));
        g.sus.end(); g.cancelCharge(0.0);
        check("G: running cleanup twice changes nothing", !g.sus.active() && g.chargingId == null && near(g.chakra, gChakra));

        System.out.println(run + " checks, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
