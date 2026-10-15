package dev.shinobi;

/** Pure rules for the head-tracking headgear display (band + dojutsu eyes). */
public final class HeadgearRules {
    private HeadgearRules() {}

    /**
     * The headgear is drawn only when the head is in its normal upright pose. Swimming/crawling, gliding and sleeping move the
     * head far from the standing eye position, so the display is hidden rather than left floating in the wrong place.
     */
    public static boolean visible(boolean swimming, boolean gliding, boolean sleeping, boolean dead, boolean spectator, boolean invisible) {
        return !(swimming || gliding || sleeping || dead || spectator || invisible);
    }

    /** Item-model id (namespace shinobi) for an {@link EyeStyle} key. */
    public static String modelId(String styleKey) {
        return "headgear_" + styleKey;
    }
}
