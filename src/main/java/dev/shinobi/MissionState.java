package dev.shinobi;

import java.util.LinkedHashMap;
import java.util.Map;

/** One accepted mission, stored inside a player's profile. */
public final class MissionState {
    public String id;          // dayKey:templateId, unique per day
    public String templateId;
    public String title;
    public String difficulty;
    public String target;
    public String world;       // delivery only
    public MissionType type;
    public int required;
    public int progress;
    public int reward;
    public int destX;          // delivery only
    public int destZ;

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("template", templateId);
        m.put("title", title);
        m.put("difficulty", difficulty);
        m.put("target", target);
        m.put("type", type.name());
        m.put("required", required);
        m.put("progress", progress);
        m.put("reward", reward);
        if (world != null) {
            m.put("world", world);
            m.put("dest-x", destX);
            m.put("dest-z", destZ);
        }
        return m;
    }

    public static MissionState fromMap(Map<?, ?> m) {
        try {
            MissionState s = new MissionState();
            s.id = String.valueOf(m.get("id"));
            s.templateId = String.valueOf(m.get("template"));
            s.title = String.valueOf(m.get("title"));
            s.difficulty = String.valueOf(m.get("difficulty"));
            s.target = String.valueOf(m.get("target"));
            s.type = MissionType.valueOf(String.valueOf(m.get("type")));
            s.required = num(m.get("required"));
            s.progress = num(m.get("progress"));
            s.reward = num(m.get("reward"));
            if (m.get("world") != null) {
                s.world = String.valueOf(m.get("world"));
                s.destX = num(m.get("dest-x"));
                s.destZ = num(m.get("dest-z"));
            }
            return s;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int num(Object o) {
        return o instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(o));
    }
}
