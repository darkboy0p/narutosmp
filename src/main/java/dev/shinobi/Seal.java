package dev.shinobi;

import java.util.Locale;

public enum Seal {
    RAT("Rat"), OX("Ox"), TIGER("Tiger"), HARE("Hare"), DRAGON("Dragon"), SNAKE("Snake"),
    HORSE("Horse"), RAM("Ram"), MONKEY("Monkey"), BIRD("Bird"), DOG("Dog"), BOAR("Boar");

    public final String display;

    Seal(String display) {
        this.display = display;
    }

    /**
     * Seal mode parks the player on hotbar slot 9, so keys 1-8 are the inputs.
     * Keys 1-8 = seals 1-8. Shift + keys 1-4 = seals 9-12.
     */
    public static Seal fromInput(int slot, boolean shift) {
        if (slot < 0 || slot > 7) return null;
        int idx = (shift && slot < 4) ? 8 + slot : slot;
        return values()[idx];
    }

    /** The key(s) that form this seal while in seal mode. */
    public String keyText() {
        int i = ordinal();
        return i < 8 ? String.valueOf(i + 1) : "Shift+" + (i - 7);
    }

    public static Seal parse(String s) {
        if (s == null) return null;
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
