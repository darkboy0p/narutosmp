package dev.shinobi;

import org.bukkit.entity.Player;

import java.util.List;

public final class Jutsu {
    @FunctionalInterface
    public interface Effect {
        /** Return true if the technique actually fired (chakra + cooldown are only spent then). */
        boolean cast(ShinobiPlugin plugin, Player p, Profile pr);
    }

    /** Outcome of a charge, handed to the release effect. Scales are already clamped by the {@link ChargeSpec}. */
    public record ChargeResult(int ticks, double progress, double damageScale, double rangeScale, boolean full) {}

    @FunctionalInterface
    public interface ChargeEffect {
        /** Called once when a charge is released. Return false if nothing happened (invalid target etc.); the engine then refunds per config. */
        boolean release(ShinobiPlugin plugin, Player p, Profile pr, ChargeResult charge);
    }

    public final String id;
    public final String name;
    public final List<Seal> seals;
    public final Nature nature;      // null = no nature requirement
    public final Clan clan;          // null = no clan requirement
    public final double cost;
    public final long cooldownMs;
    public final boolean needsKnown; // must be learned from a scroll / admin
    public final boolean needsDojutsu;
    public final Effect effect;
    public final ChargeSpec charge;          // null = not chargeable
    public final ChargeEffect chargeEffect;
    public final java.util.function.ToLongFunction<Profile> cooldownResolver; // optional per-player cooldown (ms); null = cooldownMs
    public final String mangekyoPower;       // Mangekyo power the caster must hold (null = none)
    public final java.util.function.Function<Profile, String> redirect; // alias: resolves to another jutsu id for this player
    public final String bloodline;           // bloodline id required to cast (null = none)
    public final String requireJutsu;        // progression: must have cast this technique...
    public final int requireCasts;           // ...at least this many times

    private Jutsu(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.seals = List.copyOf(b.seals);
        this.nature = b.nature;
        this.clan = b.clan;
        this.cost = b.cost;
        this.cooldownMs = b.cooldownMs;
        this.needsKnown = b.needsKnown;
        this.needsDojutsu = b.needsDojutsu;
        this.effect = b.effect;
        this.charge = b.charge;
        this.chargeEffect = b.chargeEffect;
        this.cooldownResolver = b.cooldownResolver;
        this.mangekyoPower = b.mangekyoPower;
        this.redirect = b.redirect;
        this.bloodline = b.bloodline;
        this.requireJutsu = b.requireJutsu;
        this.requireCasts = b.requireCasts;
    }

    public String sealText() {
        if (seals.isEmpty()) return "no seals";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < seals.size(); i++) {
            if (i > 0) sb.append(" > ");
            sb.append(seals.get(i).display).append(" [").append(seals.get(i).keyText()).append("]");
        }
        return sb.toString();
    }

    public static Builder builder(String id, String name) {
        return new Builder(id, name);
    }

    public static final class Builder {
        private final String id;
        private final String name;
        private List<Seal> seals = List.of();
        private Nature nature;
        private Clan clan;
        private double cost = 20;
        private long cooldownMs = 5000;
        private boolean needsKnown = true;
        private boolean needsDojutsu = false;
        private Effect effect;
        private ChargeSpec charge;
        private ChargeEffect chargeEffect;
        private java.util.function.ToLongFunction<Profile> cooldownResolver;
        private String mangekyoPower;
        private java.util.function.Function<Profile, String> redirect;
        private String bloodline;
        private String requireJutsu;
        private int requireCasts;

        private Builder(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public Builder seals(Seal... s) { this.seals = List.of(s); return this; }
        public Builder nature(Nature n) { this.nature = n; return this; }
        public Builder clan(Clan c) { this.clan = c; return this; }
        public Builder cost(double c) { this.cost = c; return this; }
        public Builder cooldown(long ms) { this.cooldownMs = ms; return this; }
        public Builder known(boolean b) { this.needsKnown = b; return this; }
        public Builder dojutsu(boolean b) { this.needsDojutsu = b; return this; }
        public Builder effect(Effect e) { this.effect = e; return this; }
        public Builder charge(ChargeSpec spec, ChargeEffect e) { this.charge = spec; this.chargeEffect = e; return this; }
        public Builder cooldownFor(java.util.function.ToLongFunction<Profile> f) { this.cooldownResolver = f; return this; }
        public Builder mangekyoPower(String power) { this.mangekyoPower = power; return this; }
        public Builder redirect(java.util.function.Function<Profile, String> f) { this.redirect = f; return this; }
        public Builder bloodline(String id) { this.bloodline = id; return this; }
        public Builder requires(String jutsuId, int casts) { this.requireJutsu = jutsuId; this.requireCasts = casts; return this; }
        public Jutsu build() { return new Jutsu(this); }
    }
}
