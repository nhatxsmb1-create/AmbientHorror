package dev.ambienthorror.manager;

import dev.ambienthorror.AmbientHorror;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PresenceManager implements Listener {

    private final AmbientHorror plugin;
    private final PresenceStorage storage;

    private final Map<UUID, Double> presenceMap = new HashMap<>();
    private final Map<UUID, Location> lastLocation = new HashMap<>();
    private final Map<UUID, Integer> lastTier = new HashMap<>();

    public PresenceManager(AmbientHorror plugin) {
        this.plugin = plugin;
        this.storage = new PresenceStorage(plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        double saved = storage.loadPresence(player.getUniqueId());
        presenceMap.put(player.getUniqueId(), saved);
        lastTier.put(player.getUniqueId(), getTierFromValue(saved));
        plugin.debug("[Presence] " + player.getName() + " joined → " +
                String.format("%.1f", saved));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        double presence = getPresence(player);
        if (plugin.getConfigManager().getPresenceConfig()
                .getBoolean("save-on-disconnect", true)) {
            storage.savePresence(player.getUniqueId(), player.getName(), presence);
        }
        presenceMap.remove(player.getUniqueId());
        lastLocation.remove(player.getUniqueId());
        lastTier.remove(player.getUniqueId());
    }

    public void tickPresence(Player player) {
        FileConfiguration cfg = plugin.getConfigManager().getPresenceConfig();
        double current = getPresence(player);
        double delta = 0;

        boolean isNight = isNight(player);
        boolean isDark = isDark(player, cfg);
        boolean isStill = isStandingStill(player);
        boolean isIndoors = isIndoors(player, cfg);
        boolean isCrowded = isCrowded(player);
        double directorScore = plugin.getHorrorDirector().calculateScore(player);
        double threshold = plugin.getConfigManager().getMinScoreThreshold();

        if (directorScore >= threshold) delta += cfg.getDouble("increase.passive", 0.5);
        if (isStill && isNight)         delta += cfg.getDouble("increase.standing-still", 0.8);
        if (isDark)                     delta += cfg.getDouble("increase.darkness", 0.6);

        if (!isNight)                   delta -= cfg.getDouble("decrease.daytime", 0.5);
        if (directorScore < threshold)  delta -= cfg.getDouble("decrease.safe-environment", 0.3);
        if (isCrowded)                  delta -= cfg.getDouble("decrease.crowded", 0.4);
        if (isIndoors)                  delta -= cfg.getDouble("decrease.indoors", 0.6);

        double newPresence = Math.max(0, Math.min(100, current + delta));
        presenceMap.put(player.getUniqueId(), newPresence);
        lastLocation.put(player.getUniqueId(), player.getLocation().clone());
        checkTierChange(player, current, newPresence);

        plugin.debug(String.format(
            "[Presence] %s %.1f→%.1f (Δ%.2f) night=%b dark=%b still=%b indoors=%b",
            player.getName(), current, newPresence, delta, isNight, isDark, isStill, isIndoors
        ));
    }

    public double getPresence(Player player) {
        return presenceMap.getOrDefault(player.getUniqueId(), 0.0);
    }

    public void setPresence(Player player, double value) {
        double clamped = Math.max(0, Math.min(100, value));
        presenceMap.put(player.getUniqueId(), clamped);
        plugin.debug("[Presence] Set " + player.getName() + " → " +
                String.format("%.1f", clamped));
    }

    public void addPresence(Player player, double amount) {
        setPresence(player, getPresence(player) + amount);
    }

    public void reducePresence(Player player, double amount) {
        setPresence(player, getPresence(player) - amount);
    }

    public void clearPresence(Player player) {
        presenceMap.remove(player.getUniqueId());
    }

    public int getPresenceTier(Player player) {
        return getTierFromValue(getPresence(player));
    }

    public int getTierFromValue(double presence) {
        if (presence < 20) return 0;
        if (presence < 40) return 1;
        if (presence < 60) return 2;
        if (presence < 80) return 3;
        return 4;
    }

    public String getTierName(int tier) {
        return plugin.getConfigManager().getPresenceConfig()
                .getString("tiers." + tier + ".name", "Unknown");
    }

    private boolean isNight(Player player) {
        long time = player.getWorld().getTime();
        return time >= 13000 && time <= 23000;
    }

    private boolean isDark(Player player, FileConfiguration cfg) {
        int threshold = cfg.getInt("darkness-threshold", 7);
        return player.getLocation().getBlock().getLightLevel() <= threshold;
    }

    private boolean isStandingStill(Player player) {
        Location prev = lastLocation.get(player.getUniqueId());
        if (prev == null) return false;
        if (!prev.getWorld().equals(player.getWorld())) return false;
        double threshold = plugin.getConfigManager().getPresenceConfig()
                .getDouble("still-distance-threshold", 1.0);
        return player.getLocation().distanceSquared(prev) <= (threshold * threshold);
    }

    private boolean isIndoors(Player player, FileConfiguration cfg) {
        int height = cfg.getInt("indoors-check-height", 5);
        Location loc = player.getLocation().clone();
        for (int i = 1; i <= height; i++) {
            Block above = loc.clone().add(0, i, 0).getBlock();
            if (above.getType().isSolid() && !above.getType().name().contains("LEAVES")) {
                return true;
            }
        }
        return false;
    }

    private boolean isCrowded(Player player) {
        int radius = plugin.getConfigManager().getNearbyPlayerRadius();
        long nearby = player.getWorld().getPlayers().stream()
                .filter(p -> !p.equals(player))
                .filter(p -> p.getLocation().distanceSquared(player.getLocation()) <= (radius * radius))
                .count();
        return nearby >= 3;
    }

    private void checkTierChange(Player player, double oldPresence, double newPresence) {
        int oldTier = getTierFromValue(oldPresence);
        int newTier = getTierFromValue(newPresence);
        if (newTier != oldTier) {
            lastTier.put(player.getUniqueId(), newTier);
            String tierName = getTierName(newTier);
            if (newTier > oldTier) {
                plugin.debug("[Presence] " + player.getName() +
                        " tier UP: " + oldTier + "→" + newTier + " (" + tierName + ")");
            } else {
                plugin.debug("[Presence] " + player.getName() +
                        " tier DOWN: " + oldTier + "→" + newTier + " (" + tierName + ")");
            }
        }
    }
    }
