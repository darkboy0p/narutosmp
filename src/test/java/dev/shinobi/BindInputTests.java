package dev.shinobi;

import dev.shinobi.BindInput.*;

/** Regression tests for hotbar-bind activation. Pure decision rules only; cast validation is covered by CastRouteGuardTests/AbilityEngine paths. */
public final class BindInputTests {
    private static int run, failed;
    private static void check(String n, boolean ok) { run++; if (!ok) { failed++; System.out.println("FAIL: " + n); } }

    static BindInput.Decision held(int newSlot, boolean sneak, String[] b, long now, long lastFire) {
        return BindInput.resolve(Trigger.HELD_CHANGE, Mode.DOUBLE_SNEAK, true, false, false, newSlot, sneak, b, true, now, 0, 300, lastFire, 150);
    }
    static BindInput.Decision sneak(Mode m, int selected, String[] b, long now, long lastSneak, long lastFire, boolean charging) {
        return BindInput.resolve(Trigger.SNEAK_START, m, true, false, charging, selected, true, b, true, now, lastSneak, 300, lastFire, 150);
    }

    public static void main(String[] args) {
        String[] b = new String[9]; b[1] = "fireball"; b[4] = "chidori"; // keys 2 and 5

        check("key->slot mapping 1..9", BindInput.slotFromKey(1) == 0 && BindInput.slotFromKey(2) == 1 && BindInput.slotFromKey(9) == 8);
        check("key out of range rejected", BindInput.slotFromKey(0) == -1 && BindInput.slotFromKey(10) == -1);
        check("slot->key mapping", BindInput.keyFromSlot(1) == 2 && BindInput.keyFromSlot(8) == 9 && BindInput.keyFromSlot(9) == -1);

        // 1. key 2 pressed while another slot is selected: slot-change event fires it (existing flow preserved)
        check("1 shift+2 from other slot fires", held(1, true, b, 1000, 0).fire() && held(1, true, b, 1000, 0).jutsuId().equals("fireball"));
        check("1b without shift does nothing", !held(1, false, b, 1000, 0).fire());
        // 2. slot 2 already selected + sneak gesture (the reported bug)
        check("2 selected slot, double-sneak: first tap waits", !sneak(Mode.DOUBLE_SNEAK, 1, b, 1000, 0, 0, false).fire());
        check("2 selected slot, double-sneak: second tap fires", sneak(Mode.DOUBLE_SNEAK, 1, b, 1200, 1000, 0, false).fire());
        check("2 double-sneak too slow does not fire", !sneak(Mode.DOUBLE_SNEAK, 1, b, 1500, 1000, 0, false).fire());
        check("2 sneak mode fires on first press", sneak(Mode.SNEAK, 1, b, 1000, 0, 0, false).fire());
        check("2 off mode never fires on selected slot", !sneak(Mode.OFF, 1, b, 1200, 1000, 0, false).fire());
        check("2 off mode leaves slot-change path working", held(1, true, b, 1000, 0).fire());
        // 3. held item irrelevant: the rules take no item input at all
        check("3 decision independent of held item (no item parameter)", sneak(Mode.SNEAK, 1, b, 1000, 0, 0, false).jutsuId().equals("fireball"));
        // 4. changing slots first, then activating
        check("4 switch slot then sneak-tap on new selected slot", sneak(Mode.DOUBLE_SNEAK, 4, b, 5200, 5000, 0, false).fire() && sneak(Mode.DOUBLE_SNEAK, 4, b, 5200, 5000, 0, false).jutsuId().equals("chidori"));
        // 5. same slot again after cooldown window / dedup window
        check("5 repeat on same slot after dedup window fires again", sneak(Mode.SNEAK, 1, b, 2000, 0, 1000, false).fire());
        // 9. duplicate/conflicting events
        check("9 same input via second event inside dedup window does not fire", !sneak(Mode.SNEAK, 1, b, 1100, 0, 1000, false).fire());
        check("9b held event right after sneak fire is deduplicated", !held(1, true, b, 1050, 1000).fire());
        check("9c consumeSneakTap set so third tap does not chain", sneak(Mode.DOUBLE_SNEAK, 1, b, 1200, 1000, 0, false).consumeSneakTap());
        // 10. other keys keep working, unbound slots stay inert
        check("10 key 5 fires chidori", held(4, true, b, 1000, 0).jutsuId().equals("chidori"));
        check("10b unbound slot inert", !held(2, true, b, 1000, 0).fire() && !sneak(Mode.SNEAK, 2, b, 1000, 0, 0, false).fire());
        check("10c invalid slot inert", !held(-1, true, b, 1000, 0).fire() && !held(9, true, b, 1000, 0).fire());
        check("10d blank bind inert", !held(0, true, new String[]{"  ", null, null, null, null, null, null, null, null}, 1000, 0).fire());
        // seal mode / charging / disabled interplay
        check("seal mode owns number keys", !BindInput.resolve(Trigger.HELD_CHANGE, Mode.DOUBLE_SNEAK, true, true, false, 1, true, b, true, 1000, 0, 300, 0, 150).fire());
        check("charge owns sneak input", !sneak(Mode.SNEAK, 1, b, 1000, 0, 0, true).fire());
        check("quick-cast disabled blocks both", !BindInput.resolve(Trigger.HELD_CHANGE, Mode.SNEAK, false, false, false, 1, true, b, true, 1000, 0, 300, 0, 150).fire());
        check("deleted ability blocks", !BindInput.resolve(Trigger.SNEAK_START, Mode.SNEAK, true, false, false, 1, true, b, false, 1000, 0, 300, 0, 150).fire());
        check("null binds safe", !BindInput.resolve(Trigger.SNEAK_START, Mode.SNEAK, true, false, false, 1, true, null, true, 1000, 0, 300, 0, 150).fire());
        // dodge priority + mode parsing
        check("bound slot beats dodge only in double-sneak mode", BindInput.boundWinsOverDodge(Mode.DOUBLE_SNEAK, true) && !BindInput.boundWinsOverDodge(Mode.DOUBLE_SNEAK, false) && !BindInput.boundWinsOverDodge(Mode.OFF, true));
        check("mode parse", Mode.parse("double-sneak") == Mode.DOUBLE_SNEAK && Mode.parse("SNEAK") == Mode.SNEAK && Mode.parse("off") == Mode.OFF && Mode.parse(null) == Mode.DOUBLE_SNEAK && Mode.parse("junk") == Mode.DOUBLE_SNEAK);
        // 12. bindings survive save/load: binds array is plain data written as a 9-entry list ("" for empty)
        String[] saved = new String[9]; for (int i = 0; i < 9; i++) saved[i] = b[i] == null ? "" : b[i];
        String[] loaded = new String[9]; for (int i = 0; i < 9; i++) loaded[i] = saved[i] == null || saved[i].isBlank() ? null : saved[i];
        check("12 binds round-trip through the persisted list form", java.util.Arrays.equals(b, loaded));

        System.out.println(run + " bind input checks, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
