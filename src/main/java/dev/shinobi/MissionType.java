package dev.shinobi;

public enum MissionType {
    HUNT, GATHER, DELIVERY, CAST, MEDITATE, TREE_CLIMB, WATER_WALK, WATERFALL, DUMMY, WEIGHTS;

    /** Missions that tick up once per second of training. */
    public boolean timeBased() {
        return this == MEDITATE || this == TREE_CLIMB || this == WATER_WALK || this == WATERFALL || this == WEIGHTS;
    }
}
