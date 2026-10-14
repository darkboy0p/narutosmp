package dev.shinobi;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The single place where an ability cast is validated, charged and committed.
 *
 * <p>Order: player state -> world rules -> sealed -> learned/copied -> nature -> clan -> dojutsu -> cooldown -> chakra.
 * Nothing is spent until the effect reports success. The cooldown is reserved <i>before</i> the effect runs (so an
 * effect that re-enters the engine cannot double-cast) and rolled back if the effect declines or throws.
 */
public final class AbilityEngine {
    private final ShinobiPlugin plugin;
    private final Set<UUID> casting = new HashSet<>();
    private long accepted;
    private long rejected;
    private long failedEffects;
    private long chargesStarted;
    private long chargesReleased;
    private long chargesAborted;

    public AbilityEngine(ShinobiPlugin plugin) {
        this.plugin = plugin;
    }

    public String stats() {
        return "casts=" + accepted + " rejected=" + rejected + " effectErrors=" + failedEffects
                + " charges(started/released/aborted)=" + chargesStarted + "/" + chargesReleased + "/" + chargesAborted;
    }

    public boolean cast(Player p, Profile pr, Jutsu j) {
        boolean ok = tryCast(p, pr, j);
        if (ok) accepted++;
        else rejected++;
        return ok;
    }

    private boolean tryCast(Player p, Profile pr, Jutsu j) {
        if (!p.isOnline() || p.isDead() || p.getGameMode() == GameMode.SPECTATOR) return false;
        if (j.redirect != null) { // alias jutsu: validate and charge the real one (so cooldowns can never be dodged by alternating names)
            String target = j.redirect.apply(pr);
            Jutsu real = target == null ? null : plugin.jutsus().get(target);
            if (real == null || real == j) {
                Msg.bar(p, "<red>You have not awakened a Mangekyou power.");
                return false;
            }
            j = real;
        }
        if (j.effect == null) return false;

        List<String> blocked = plugin.getConfig().getStringList("abilities.disabled-worlds");
        if (blocked.contains(p.getWorld().getName())) {
            Msg.bar(p, "<red>Jutsu are disabled in this world.");
            return false;
        }

        UUID id = p.getUniqueId();
        if (plugin.charging().isCharging(id)) {
            Msg.bar(p, "<red>Release or cancel your current charge first.");
            return false;
        }
        if (!casting.add(id)) return false; // re-entrant / duplicated cast
        try {
            return validateAndRun(p, pr, j);
        } finally {
            casting.remove(id);
        }
    }

    /** Everything a cast needs once validation has passed. */
    public record Prep(double cost, long cooldownMs, boolean copied) {}

    private boolean validateAndRun(Player p, Profile pr, Jutsu j) {
        Prep prep = prepare(p, pr, j);
        if (prep == null) return false;
        long now = System.currentTimeMillis();
        if (plugin.charging().enabledFor(j)) return startCharge(p, pr, j, prep);
        return runInstant(p, pr, j, prep, now);
    }

    /** Validates a cast (state, learned, nature, clan, progression, dojutsu, cooldown, chakra). Returns null if rejected. */
    private Prep prepare(Player p, Profile pr, Jutsu j) {
        JutsuService svc = plugin.casting();
        long now = System.currentTimeMillis();
        if (now < pr.sealedUntil) {
            Msg.bar(p, "<red>Your chakra points are sealed!");
            return null;
        }
        // Explicit compatibility table: only listed abilities are refused while Susanoo is active; everything else casts normally.
        if (AbilityCompat.blocked(plugin.susanoo().isActive(p), j.id, plugin.susanoo().incompatible())) {
            Msg.bar(p, "<red>" + j.name + " cannot be used while Susanoo is active.");
            return null;
        }
        // One central access decision for every route: natural, or administrator-forced (per ability / per bloodline).
        AccessRules.Decision acc = plugin.access().decide(pr, j);
        boolean copied = false;
        if (j.needsKnown && !acc.skipKnown() && !svc.unlocked(pr, j)) {
            Long exp = pr.copied.get(j.id);
            if (exp != null && exp > now) {
                copied = true;
            } else {
                Msg.bar(p, "<red>You haven't learned <white>" + j.name + "<red>.");
                return null;
            }
        }
        if (j.nature != null && !acc.skipNature() && pr.nature != j.nature && !svc.easy("ignore-nature")) {
            Msg.bar(p, "<red>Your chakra nature does not match <white>" + j.name + "<red>.");
            return null;
        }
        if (j.clan != null && !acc.skipClan() && (pr.clan != j.clan || !pr.awakened)) {
            Msg.bar(p, "<red>Only an awakened " + j.clan.display + " can use that.");
            return null;
        }
        if (j.bloodline != null && !acc.skipBloodline()) {
            BloodlineRules.Result br = plugin.bloodlines().check(pr, j.bloodline);
            if (br != BloodlineRules.Result.OK) {
                Msg.bar(p, "<red>" + j.name + ": " + BloodlineRules.describe(br) + ".");
                return null;
            }
        }
        if (j.mangekyoPower != null && !acc.skipKnown() && !pr.mangekyoPowers.contains(j.mangekyoPower)) {
            Msg.bar(p, "<red>You do not hold the " + MangekyoManager.display(j.mangekyoPower) + " power.");
            return null;
        }
        if (j.requireJutsu != null && !acc.skipProgression() && pr.masteryOf(j.requireJutsu) < j.requireCasts) {
            Jutsu base = plugin.jutsus().get(j.requireJutsu);
            Msg.bar(p, "<red>" + j.name + " needs <white>" + (base == null ? j.requireJutsu : base.name) + "<red> mastery: "
                    + pr.masteryOf(j.requireJutsu) + "/" + j.requireCasts + " casts.");
            return null;
        }
        if (j.needsDojutsu && !pr.dojutsuActive) {
            Msg.bar(p, "<red>Activate your dojutsu first.");
            return null;
        }
        boolean ignoreCooldown = acc.forced() && !plugin.getConfig().getBoolean("admin.forced-abilities-respect-cooldowns", true);
        Long cd = pr.cooldowns.get(j.id);
        if (!ignoreCooldown && cd != null && cd > now) {
            Msg.bar(p, "<red>" + j.name + " on cooldown: " + String.format(java.util.Locale.ROOT, "%.1f", (cd - now) / 1000.0) + "s");
            return null;
        }

        double cost = svc.costFor(pr, j) * (copied ? 1.5 : 1.0);
        long cooldown = j.cooldownResolver != null ? j.cooldownResolver.applyAsLong(pr) : j.cooldownMs;
        if (pr.eternal && MangekyoManager.ABILITIES.contains(j.id)) {
            cost *= 0.85;
            cooldown = (long) (cooldown * 0.7);
        }
        if (Double.isNaN(cost) || cost < 0) cost = 0;
        cooldown = Math.max(0L, cooldown);
        if (acc.forced() && !plugin.getConfig().getBoolean("admin.forced-abilities-require-chakra", true)) cost = 0;
        if (ignoreCooldown) cooldown = 0;
        if (pr.chakra < cost) {
            Msg.bar(p, "<red>Not enough chakra (" + (int) Math.ceil(cost) + " needed).");
            return null;
        }
        return new Prep(cost, cooldown, copied);
    }

    private boolean runInstant(Player p, Profile pr, Jutsu j, Prep prep, long now) {
        double cost = prep.cost();
        long cooldown = prep.cooldownMs();
        boolean copied = prep.copied();
        // Reserve the cooldown, run the effect, roll back on failure.
        Long previous = pr.cooldowns.put(j.id, now + cooldown);
        boolean fired;
        try {
            fired = j.effect.cast(plugin, p, pr);
        } catch (RuntimeException e) {
            failedEffects++;
            plugin.getLogger().log(Level.SEVERE, "Ability '" + j.id + "' threw for " + p.getName() + "; nothing was charged.", e);
            plugin.effects().cleanup(p.getUniqueId(), EffectRegistry.Reason.ERROR);
            fired = false;
        }
        if (!fired) {
            if (previous == null) pr.cooldowns.remove(j.id);
            else pr.cooldowns.put(j.id, previous);
            return false;
        }

        pr.chakra = Math.max(0.0, pr.chakra - cost);
        commit(p, pr, j, copied, now);
        return true;
    }

    /** Shared tail of a successful cast: mastery, feedback, missions, Sharingan copying. */
    private void commit(Player p, Profile pr, Jutsu j, boolean copied, long now) {
        if (!copied) pr.mastery.merge(j.id, 1, Integer::sum);
        if (j.cost > 0) {
            Msg.bar(p, "<gold>" + j.name + "!");
            plugin.missions().progress(p, pr, MissionType.CAST, j.id, 1);
        }
        if (j.needsKnown) plugin.casting().shareWithSharingan(p, j, now);
    }

    // ------------------------------------------------------------------ charging

    /** Reserves the chakra up front and hands the session to the ChargeManager. Cooldown starts on release. */
    private boolean startCharge(Player p, Profile pr, Jutsu j, Prep prep) {
        ChargeLedger ledger = new ChargeLedger(prep.cost());
        pr.chakra = Math.max(0.0, pr.chakra - prep.cost());
        if (!plugin.charging().begin(p, pr, j, prep, ledger)) {
            giveBack(pr, ledger.refund(1.0));
            return false;
        }
        chargesStarted++;
        return true;
    }

    /**
     * Called by the ChargeManager when a charge is released at or above its minimum. The session is already removed,
     * so this can run at most once per charge. Returns true if the technique fired.
     */
    boolean settleRelease(Player p, Profile pr, ChargeSession s) {
        Jutsu j = s.jutsu;
        Jutsu.ChargeResult result = new Jutsu.ChargeResult(s.elapsed, s.spec.progress(s.elapsed),
                s.spec.damageScale(s.elapsed), s.spec.rangeScale(s.elapsed), s.spec.full(s.elapsed));
        boolean fired;
        try {
            fired = j.chargeEffect.release(plugin, p, pr, result);
        } catch (RuntimeException ex) {
            failedEffects++;
            plugin.getLogger().log(Level.SEVERE, "Charge release '" + j.id + "' threw for " + p.getName(), ex);
            plugin.effects().cleanup(p.getUniqueId(), EffectRegistry.Reason.ERROR);
            fired = false;
        }
        if (!fired) {
            settleCancel(p, pr, s, "failed-release");
            return false;
        }
        if (!s.ledger.commit()) return true; // already settled elsewhere: never double-charge
        long now = System.currentTimeMillis();
        pr.cooldowns.put(j.id, now + s.cooldownMs);
        chargesReleased++;
        commit(p, pr, j, s.copied, now);
        return true;
    }

    /**
     * Ends a charge without firing: refunds the configured fraction (key under {@code charging.refund}) and, for
     * player-caused endings, applies a short anti-spam cooldown. Safe to call more than once (the ledger settles once).
     */
    void settleCancel(Player p, Profile pr, ChargeSession s, String refundKey) {
        double fraction = plugin.getConfig().getDouble("charging.refund." + refundKey, defaultRefund(refundKey));
        giveBack(pr, s.ledger.refund(fraction));
        chargesAborted++;
        if (!refundKey.equals("forced")) {
            long cd = Math.max(0L, plugin.getConfig().getLong("charging.cancel-cooldown-ms", 1500L));
            long until = System.currentTimeMillis() + cd;
            Long cur = pr.cooldowns.get(s.jutsu.id);
            if (cd > 0 && (cur == null || cur < until)) pr.cooldowns.put(s.jutsu.id, until);
        }
    }

    private static double defaultRefund(String key) {
        return switch (key) {
            case "cancelled", "fizzled", "failed-release" -> 0.5;
            default -> 0.0; // interrupted, forced
        };
    }

    private void giveBack(Profile pr, double amount) {
        if (amount <= 0) return;
        pr.chakra = Math.min(plugin.chakra().effectiveMax(pr), pr.chakra + amount);
    }
}
