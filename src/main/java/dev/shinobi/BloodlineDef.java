package dev.shinobi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Pure (Bukkit-free) definition of one bloodline / kekkei genkai stage. Immutable. */
public final class BloodlineDef {
    public enum Rarity { COMMON, UNCOMMON, RARE, EPIC, LEGENDARY }

    /** CORE = part of a clan's identity (Sharingan, Byakugan...). RARE = optional advanced bloodline. */
    public enum Kind { CORE, RARE }

    public final String id;
    public final String display;
    public final Kind kind;
    public final Rarity rarity;
    public final Set<Clan> eligibleClans;
    public final boolean anyClan;      // owner-configured exception: any clan may hold it
    public final boolean enabled;
    public final boolean innate;       // granted automatically to eligible awakened clan members
    public final String parent;        // evolution prerequisite (bloodline id) or null
    public final List<String> abilities; // jutsu ids that belong to this bloodline

    private BloodlineDef(Builder b) {
        this.id = b.id;
        this.display = b.display;
        this.kind = b.kind;
        this.rarity = b.rarity;
        this.eligibleClans = Collections.unmodifiableSet(b.clans.isEmpty() ? EnumSet.noneOf(Clan.class) : EnumSet.copyOf(b.clans));
        this.anyClan = b.anyClan;
        this.enabled = b.enabled;
        this.innate = b.innate;
        this.parent = b.parent == null || b.parent.isBlank() ? null : b.parent;
        this.abilities = List.copyOf(b.abilities);
    }

    public static Builder builder(String id, String display, Kind kind) {
        return new Builder(id, display, kind);
    }

    public Builder toBuilder() {
        Builder b = new Builder(id, display, kind);
        b.rarity = rarity;
        b.clans.addAll(eligibleClans);
        b.anyClan = anyClan;
        b.enabled = enabled;
        b.innate = innate;
        b.parent = parent;
        b.abilities.addAll(abilities);
        return b;
    }

    public static final class Builder {
        private final String id;
        private String display;
        private final Kind kind;
        private Rarity rarity = Rarity.RARE;
        private final Set<Clan> clans = EnumSet.noneOf(Clan.class);
        private boolean anyClan;
        private boolean enabled = true;
        private boolean innate;
        private String parent;
        private final List<String> abilities = new ArrayList<>();

        private Builder(String id, String display, Kind kind) {
            this.id = id;
            this.display = display;
            this.kind = kind;
        }

        public Builder display(String v) { this.display = v; return this; }
        public Builder rarity(Rarity v) { this.rarity = v; return this; }
        public Builder clans(Clan... v) { this.clans.clear(); this.clans.addAll(List.of(v)); return this; }
        public Builder clans(Set<Clan> v) { this.clans.clear(); this.clans.addAll(v); return this; }
        public Builder anyClan(boolean v) { this.anyClan = v; return this; }
        public Builder enabled(boolean v) { this.enabled = v; return this; }
        public Builder innate(boolean v) { this.innate = v; return this; }
        public Builder parent(String v) { this.parent = v; return this; }
        public Builder abilities(List<String> v) { this.abilities.clear(); this.abilities.addAll(v); return this; }
        public BloodlineDef build() { return new BloodlineDef(this); }
    }
}
