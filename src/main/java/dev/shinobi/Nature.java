package dev.shinobi;

import java.util.Locale;
import java.util.Random;

public enum Nature {
    FIRE("Fire", "<red>", "The paper bursts into flame!"),
    WATER("Water", "<aqua>", "The paper becomes damp."),
    WIND("Wind", "<white>", "The paper splits cleanly in two."),
    LIGHTNING("Lightning", "<yellow>", "The paper crinkles and wrinkles."),
    EARTH("Earth", "<gold>", "The paper crumbles to dust.");

    public final String display;
    public final String color;
    public final String paperEffect;

    Nature(String display, String color, String paperEffect) {
        this.display = display;
        this.color = color;
        this.paperEffect = paperEffect;
    }

    public String colored() {
        return color + display;
    }

    public static Nature parse(String s) {
        if (s == null) return null;
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Nature random(Random r) {
        Nature[] v = values();
        return v[r.nextInt(v.length)];
    }
}
