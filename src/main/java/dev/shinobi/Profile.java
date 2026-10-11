package dev.shinobi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Profile {
    public final UUID id;

    // ---- persisted ----
    public boolean rolled;
    public Nature nature;
    public boolean natureRevealed;
    public Clan clan;
    public boolean awakened;
    public double maxChakra = 100.0;
    public double chakra = 100.0;
    public final Set<String> known = new HashSet<>();
    public final Map<String, Integer> mastery = new HashMap<>();
    public String mangekyo;
    public boolean eternal;
    public double strain;
    public int tomoe;                        // 0 = automatic (from training), 1-3 = admin override
    public final String[] binds = new String[9]; // jutsu id bound to each hotbar slot (Shift + number key)
    public double weightedSeconds;
    public final Set<UUID> bonds = new HashSet<>();
    public final List<MissionState> missions = new ArrayList<>();
    public final Set<String> completedMissions = new LinkedHashSet<>();

    // ---- transient (not saved) ----
    public boolean dojutsuActive;
    public int gate;
    public int gateTicks;
    public long gateLastOpen;
    public long gate8Until;
    public long sealedUntil;
    public long rasenganUntil;
    public SealSession session;
    public final Map<String, Long> cooldowns = new HashMap<>();
    public final Map<String, Long> copied = new LinkedHashMap<>(); // Sharingan-copied jutsu -> expiry
    public boolean wallWalk, waterWalk, weights;
    public boolean climbingNow, waterNow;
    public long lastDummyHit;
    public int lastX, lastY, lastZ;

    public Profile(UUID id) {
        this.id = id;
    }

    public int masteryOf(String jutsuId) {
        return mastery.getOrDefault(jutsuId, 0);
    }
}
