package dev.shinobi;

import java.util.ArrayList;
import java.util.List;

public final class SealSession {
    public final List<Seal> seq = new ArrayList<>();
    public final int originalSlot;
    public long last = System.currentTimeMillis();

    public SealSession(int originalSlot) {
        this.originalSlot = originalSlot;
    }
}
