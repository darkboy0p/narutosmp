package dev.shinobi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JutsuRegistry {
    private final Map<String, Jutsu> byId = new LinkedHashMap<>();

    public void register(Jutsu j) {
        byId.put(j.id, j);
    }

    public Jutsu get(String id) {
        return id == null ? null : byId.get(id.toLowerCase());
    }

    public Collection<Jutsu> all() {
        return byId.values();
    }

    public Jutsu matchExact(List<Seal> seq) {
        for (Jutsu j : byId.values()) {
            if (!j.seals.isEmpty() && j.seals.equals(seq)) return j;
        }
        return null;
    }

    public boolean hasPrefix(List<Seal> seq) {
        for (Jutsu j : byId.values()) {
            if (j.seals.size() > seq.size() && j.seals.subList(0, seq.size()).equals(seq)) return true;
        }
        return false;
    }

    public List<String> ids() {
        return new ArrayList<>(byId.keySet());
    }
}
