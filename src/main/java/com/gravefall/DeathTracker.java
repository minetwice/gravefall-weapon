package com.gravefall;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Counts deaths per player. Falling more than 'max-deaths' times
 * results in a ban (revivable via /gravefall revive).
 */
public class DeathTracker {

    private final Map<UUID, Integer> deaths = new HashMap<>();

    /** Records one death and returns the new total. */
    public int record(UUID uuid) {
        int next = deaths.getOrDefault(uuid, 0) + 1;
        deaths.put(uuid, next);
        return next;
    }

    public int get(UUID uuid) {
        return deaths.getOrDefault(uuid, 0);
    }

    public void reset(UUID uuid) {
        deaths.remove(uuid);
    }
}
