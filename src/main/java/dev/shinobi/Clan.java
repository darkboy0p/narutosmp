package dev.shinobi;

import java.util.Locale;

public enum Clan {
    UCHIHA("Uchiha", "<red>", "Sharingan, fire affinity discount, unique Mangekyou", 0.06),
    HYUGA("Hyuga", "<white>", "Byakugan and the Gentle Fist", 0.06),
    UZUMAKI("Uzumaki", "<gold>", "Huge chakra pool, fast regen, Chakra Chains", 0.05),
    SENJU("Senju", "<green>", "Vitality, White Light Sabre (Wood Release arrives in a later update)", 0.05),
    NARA("Nara", "<dark_green>", "Shadow Bind", 0.07);

    public final String display;
    public final String color;
    public final String abilities;
    public final double defaultChance;

    Clan(String display, String color, String abilities, double defaultChance) {
        this.display = display;
        this.color = color;
        this.abilities = abilities;
        this.defaultChance = defaultChance;
    }

    public String colored() {
        return color + display;
    }

    public boolean hasDojutsu() {
        return this == UCHIHA || this == HYUGA;
    }

    public String dojutsuName() {
        return this == UCHIHA ? "Sharingan" : this == HYUGA ? "Byakugan" : null;
    }

    /** Legacy clan name that Senju replaced. Only the data migration and admin input accept it. */
    public static final String LEGACY_HATAKE = "HATAKE";

    public static Clan parse(String s) {
        if (s == null) return null;
        try {
            String key = s.trim().toUpperCase(Locale.ROOT);
            if (key.equals(LEGACY_HATAKE)) return SENJU;
            return valueOf(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
