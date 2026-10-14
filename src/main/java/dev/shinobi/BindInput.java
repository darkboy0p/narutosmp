package dev.shinobi;

import java.util.Locale;

/**
 * Pure decision rules for hotbar-bound ability activation (no Bukkit).
 *
 * ROOT CAUSE of the reported bug: activation lived only in PlayerItemHeldEvent, which Paper fires only when the selected
 * slot CHANGES. With slot 2 already selected, pressing "2" (or sneaking) sends the server no slot event, so nothing happened.
 * Paper cannot observe a same-slot key press at all, so a server-side fix must add a second, deliberate trigger for the
 * selected slot: a sneak gesture on the selected, bound slot. Cooldown, chakra, unlock and range stay in the cast engine.
 */
public final class BindInput {
    private BindInput() {}

    public enum Mode {
        OFF, SNEAK, DOUBLE_SNEAK;
        public static Mode parse(String s) {
            if (s == null) return DOUBLE_SNEAK;
            return switch (s.trim().toLowerCase(Locale.ROOT).replace('-', '_')) {
                case "off" -> OFF;
                case "sneak" -> SNEAK;
                default -> DOUBLE_SNEAK;
            };
        }
    }
    public enum Trigger { HELD_CHANGE, SNEAK_START }

    public record Decision(boolean fire, String jutsuId, boolean consumeSneakTap, String reason) {
        static Decision no(String r) { return new Decision(false, null, false, r); }
    }

    /** Key 1-9 on the keyboard <-> inventory slot 0-8. */
    public static int slotFromKey(int key) { return key >= 1 && key <= 9 ? key - 1 : -1; }
    public static int keyFromSlot(int slot) { return slot >= 0 && slot <= 8 ? slot + 1 : -1; }

    /**
     * @param slot            HELD_CHANGE: the NEW slot being switched to. SNEAK_START: the currently selected slot.
     * @param sneaking        whether the player is sneaking (HELD_CHANGE needs Shift held; SNEAK_START is the press itself)
     * @param lastSneakMs     time of the previous sneak press (0 = none)
     * @param lastFireMs      time this player's bound ability last fired through ANY trigger (dedup)
     * @param dedupMs         identical input seen via two events inside this window fires once
     */
    public static Decision resolve(Trigger t, Mode mode, boolean quickCastEnabled, boolean sealSessionActive, boolean charging,
                                   int slot, boolean sneaking, String[] binds, boolean bindExists,
                                   long now, long lastSneakMs, long windowMs, long lastFireMs, long dedupMs) {
        if (!quickCastEnabled) return Decision.no("quick-cast disabled");
        if (sealSessionActive) return Decision.no("seal mode owns number keys");
        if (slot < 0 || binds == null || slot >= binds.length) return Decision.no("invalid slot");
        String id = binds[slot];
        if (id == null || id.isBlank()) return Decision.no("slot not bound");
        if (!bindExists) return Decision.no("bound ability no longer exists");
        if (lastFireMs > 0 && now - lastFireMs < dedupMs) return Decision.no("duplicate input");
        if (t == Trigger.HELD_CHANGE) {
            if (!sneaking) return Decision.no("shift not held");
            return new Decision(true, id, false, "");
        }
        // SNEAK_START on the already-selected slot
        if (mode == Mode.OFF) return Decision.no("selected-slot activation off");
        if (charging) return Decision.no("a charge owns the sneak input");
        if (mode == Mode.SNEAK) return new Decision(true, id, true, "");
        if (lastSneakMs > 0 && now - lastSneakMs <= windowMs) return new Decision(true, id, true, "");
        return Decision.no("waiting for second sneak tap");
    }

    /** True when a bound selected slot should take priority over the Sharingan double-sneak dodge (same gesture). */
    public static boolean boundWinsOverDodge(Mode mode, boolean slotBoundAndExists) {
        return slotBoundAndExists && mode == Mode.DOUBLE_SNEAK;
    }
}
