package dev.ambienthorror.api;

import dev.ambienthorror.AmbientHorror;
import org.bukkit.entity.Player;

public class AmbientAPI {

    private static AmbientAPI instance;
    private final AmbientHorror plugin;

    public AmbientAPI(AmbientHorror plugin) {
        this.plugin = plugin;
        instance = this;
    }

    public static AmbientAPI get() {
        return instance;
    }

    public void setPresence(Player player, double value) {
        plugin.getPresenceManager().setPresence(player, value);
    }

    public void addPresence(Player player, double amount) {
        plugin.getPresenceManager().addPresence(player, amount);
    }

    public void reducePresence(Player player, double amount) {
        plugin.getPresenceManager().reducePresence(player, amount);
    }

    public double getPresence(Player player) {
        return plugin.getPresenceManager().getPresence(player);
    }

    public int getPresenceTier(Player player) {
        return plugin.getPresenceManager().getPresenceTier(player);
    }

    public void playEvent(Player player, String eventKey) {
        var eventsConfig = plugin.getConfigManager().getEventsConfig();
        String soundKey = eventsConfig.getString("events." + eventKey + ".sound-key",
                eventKey.toLowerCase());
        plugin.getSoundManager().play(player, soundKey);
        plugin.debug("[API] Force event " + eventKey + " → " + player.getName());
    }

    public void playSound(Player player, String soundKey) {
        plugin.getSoundManager().play(player, soundKey);
    }

    public double getDirectorScore(Player player) {
        return plugin.getHorrorDirector().calculateScore(player);
    }

    public boolean isInCombat(Player player) {
        return plugin.getCombatManager().isInCombat(player);
    }

    public void markCombat(Player player) {
        plugin.getCombatManager().markCombat(player);
    }
}
