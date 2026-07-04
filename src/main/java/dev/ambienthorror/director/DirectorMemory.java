package dev.ambienthorror.director;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DirectorMemory {

    private final Map<UUID, EnumMap<DirectorAction, Long>> memory = new HashMap<>();

    private static final Map<DirectorAction, Long> DEFAULT_COOLDOWNS = Map.of(
            DirectorAction.AMBIENT_SOUND,      45_000L,
            DirectorAction.SHADOW_FLICKER,    120_000L,
            DirectorAction.SHADOW_PERIPHERAL, 180_000L,
            DirectorAction.SHADOW_BEHIND,     240_000L,
            DirectorAction.RADIO_CORRUPTED,   600_000L,
            DirectorAction.SANITY_DRAIN,       30_000L,
            DirectorAction.THE_MAN_HINT,      900_000L,
            DirectorAction.WORLD_EVENT,      1800_000L
    );

    public boolean canTrigger(UUID uuid, DirectorAction action) {
        long cooldown = DEFAULT_COOLDOWNS.getOrDefault(action, 60_000L);
        long last = getLastTime(uuid, action);
        return System.currentTimeMillis() - last >= cooldown;
    }

    public void markTriggered(UUID uuid, DirectorAction action) {
        memory.computeIfAbsent(uuid, k -> new EnumMap<>(DirectorAction.class))
                .put(action, System.currentTimeMillis());
    }

    public long getRemainingCooldown(UUID uuid, DirectorAction action) {
        long cooldown = DEFAULT_COOLDOWNS.getOrDefault(action, 60_000L);
        long last = getLastTime(uuid, action);
        return Math.max(0, cooldown - (System.currentTimeMillis() - last));
    }

    public void clearPlayer(UUID uuid) {
        memory.remove(uuid);
    }

    private long getLastTime(UUID uuid, DirectorAction action) {
        var playerMemory = memory.get(uuid);
        if (playerMemory == null) return 0L;
        return playerMemory.getOrDefault(action, 0L);
    }
}
