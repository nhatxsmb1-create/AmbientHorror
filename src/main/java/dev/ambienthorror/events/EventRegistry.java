package dev.ambienthorror.events;

import dev.ambienthorror.AmbientHorror;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EventRegistry {

    private static final Random RANDOM = new Random();

    public static AmbientEventData selectEvent(AmbientHorror plugin, Player player) {
        List<AmbientEventData> allEvents = loadEvents(plugin);

        double presence = plugin.getPresenceManager().getPresence(player);
        double score    = plugin.getHorrorDirector().calculateScore(player);

        List<AmbientEventData> eligible = allEvents.stream()
                .filter(AmbientEventData::enabled)
                .filter(e -> presence >= e.minPresence())
                .filter(e -> score >= e.minScore())
                .toList();

        if (eligible.isEmpty()) {
            plugin.debug("[EventRegistry] Không có event eligible cho " + player.getName());
            return null;
        }

        return weightedRandom(eligible);
    }

    private static List<AmbientEventData> loadEvents(AmbientHorror plugin) {
        List<AmbientEventData> list = new ArrayList<>();
        ConfigurationSection root = plugin.getConfigManager()
                .getEventsConfig().getConfigurationSection("events");

        if (root == null) {
            plugin.log("[EventRegistry] events.yml thiếu section 'events'!");
            return list;
        }

        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) continue;
            list.add(new AmbientEventData(
                    key,
                    sec.getString("sound-key", key.toLowerCase()),
                    sec.getDouble("min-presence", 0),
                    sec.getDouble("min-score", 0),
                    sec.getInt("weight", 10),
                    sec.getInt("cooldown-seconds", 60),
                    sec.getBoolean("enabled", true)
            ));
        }

        return list;
    }

    private static AmbientEventData weightedRandom(List<AmbientEventData> events) {
        int totalWeight = events.stream().mapToInt(AmbientEventData::weight).sum();
        if (totalWeight <= 0) return events.get(0);

        int roll = RANDOM.nextInt(totalWeight);
        int cumulative = 0;

        for (AmbientEventData event : events) {
            cumulative += event.weight();
            if (roll < cumulative) return event;
        }

        return events.get(events.size() - 1);
    }
}
