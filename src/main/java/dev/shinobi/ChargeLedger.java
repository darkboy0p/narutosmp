package dev.shinobi;

/**
 * Pure bookkeeping for the chakra reserved when a charge starts. A ledger can be settled exactly once:
 * either {@link #commit()} (the cast resolved, the chakra is spent) or {@link #refund(double)} (the charge ended
 * without resolving). Any second settlement is a no-op, which is what prevents double consumption and double refunds.
 */
public final class ChargeLedger {
    private final double reserved;
    private boolean settled;

    public ChargeLedger(double reserved) {
        this.reserved = Double.isNaN(reserved) || reserved < 0 ? 0.0 : reserved;
    }

    public double reserved() {
        return reserved;
    }

    public boolean isOpen() {
        return !settled;
    }

    /** Marks the reservation as spent. Returns false if it was already settled. */
    public boolean commit() {
        if (settled) return false;
        settled = true;
        return true;
    }

    /** Settles the reservation and returns how much chakra to give back (fraction clamped to 0..1). 0 if already settled. */
    public double refund(double fraction) {
        if (settled) return 0.0;
        settled = true;
        double f = Double.isNaN(fraction) ? 0.0 : Math.max(0.0, Math.min(1.0, fraction));
        return reserved * f;
    }
}
