package dev.ambienthorror.manager;

import dev.ambienthorror.AmbientHorror;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PresenceManager {

    private final AmbientHorror plugin;
    private final Map<UUID, Double> presenceMap = new HashMap<>();

    private static final double PASSIVE_INCREASE_PER_TICK = 0.5;
    private static final double PASSIVE_DECREASE_PER_TICK = 0.2;

    public PresenceManager(AmbientHorror plugin) {
        this.plugin = plugin;
    }

    public double getPresence(Player player) {
        return presenceMap.getOrDefault(player.getUniqueId(), 0.0);
    }

    public void setPresence(Player player, double value) {
        double clamped = Math.max(0, Math.min(100, value));
        presenceMap.put(player.getUniqueId(), clamped);
        plugin.debug("[Presence] " + player.getName() + " → " + String.format("%.1f", clamped));
    }

    public void addPresence(Player player, double amount) {
        setPresence(player, getPresence(player) + amount);
    }

    public void reducePresence(Player player, double amount) {
        setPresence(player, getPresence(player) - amount);
    }

    public void tickPresence(Player player) {
        double score = plugin.getHorrorDirector().calculateScore(player);
        double threshold = plugin.getConfigManager().getMinScoreThreshold();
        if (score >= threshold) {
            addPresence(player, PASSIVE_INCREASE_PER_TICK);
        } else {
            reducePresence(player, PASSIVE_DECREASE_PER_TICK);
        }
    }

    public void clearPresence(Player player) {
        presenceMap.remove(player.getUniqueId());
    }

    public int getPresenceTier(Player player) {
        double p = getPresence(player);
        if (p < 20) return 0;
        if (p < 40) return 1;
        if (p < 60) return 2;
        if (p < 80) return 3;
        return 4;
    }
}
