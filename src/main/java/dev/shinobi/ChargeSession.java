package dev.shinobi;

import java.util.UUID;

/** One player's active charge. Created and mutated only by {@link ChargeManager} on the main thread. */
final class ChargeSession {
    final UUID owner;
    final UUID worldId;
    final Jutsu jutsu;
    final ChargeSpec spec;
    final ChargeLedger ledger;
    final long cooldownMs;
    final boolean copied;

    int elapsed;                 // ticks charged so far
    int lastStage;
    boolean fullAnnounced;
    EffectRegistry.Handle handle;

    ChargeSession(UUID owner, UUID worldId, Jutsu jutsu, ChargeSpec spec, ChargeLedger ledger, long cooldownMs, boolean copied) {
        this.owner = owner;
        this.worldId = worldId;
        this.jutsu = jutsu;
        this.spec = spec;
        this.ledger = ledger;
        this.cooldownMs = cooldownMs;
        this.copied = copied;
    }
}
