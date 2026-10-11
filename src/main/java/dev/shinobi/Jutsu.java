package dev.shinobi;

import org.bukkit.entity.Player;

import java.util.List;

public final class Jutsu {
    @FunctionalInterface
    public interface Effect {
        /** Return true if the technique actually fired (chakra + cooldown are only spent then). */
        boolean cast(ShinobiPlugin plugin, Player p, Profile pr);
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
        public Jutsu build() { return new Jutsu(this); }
    }
}
