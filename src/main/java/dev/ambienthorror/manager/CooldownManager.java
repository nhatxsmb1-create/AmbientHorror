package dev.ambienthorror.manager;

import dev.ambienthorror.AmbientHorror;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {

    private final AmbientHorror plugin;
    private final Map<UUID, Long> lastEventTime = new HashMap<>();
    private final Map<String, Long> lastEventTypeTime = new HashMap<>();
    private long lastGlobalEventTime = 0;

    public CooldownManager(AmbientHorror plugin) {
        this.plugin = plugin;
    }

    public boolean canTrigger(Player player, String eventKey, int eventCooldownSeconds) {
        long now = System.currentTimeMillis();

        long globalMs = plugin.getConfigManager().getGlobalCooldownSeconds() * 1000L;
        if (now - lastGlobalEventTime < globalMs) {
            plugin.debug("[Cooldown] Global còn " +
                    ((globalMs - (now - lastGlobalEventTime)) / 1000) + "s");
            return false;
        }

        long perPlayerMs = plugin.getConfigManager().getPerPlayerCooldownSeconds() * 1000L;
        long lastPlayer = lastEventTime.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastPlayer < perPlayerMs) {
            plugin.debug("[Cooldown] " + player.getName() + " per-player còn " +
                    ((perPlayerMs - (now - lastPlayer)) / 1000) + "s");
            return false;
        }

        String key = player.getUniqueId() + ":" + eventKey;
        long eventMs = eventCooldownSeconds * 1000L;
        long lastEvent = lastEventTypeTime.getOrDefault(key, 0L);
        if (now - lastEvent < eventMs) {
            plugin.debug("[Cooldown] Event " + eventKey + " còn " +
                    ((eventMs - (now - lastEvent)) / 1000) + "s");
            return false;
        }

        return true;
    }

    public void markTriggered(Player player, String eventKey) {
        long now = System.currentTimeMillis();
        lastGlobalEventTime = now;
        lastEventTime.put(player.getUniqueId(), now);
        lastEventTypeTime.put(player.getUniqueId() + ":" + eventKey, now);
    }

    public void clearPlayer(Player player) {
        lastEventTime.remove(player.getUniqueId());
    }
}
