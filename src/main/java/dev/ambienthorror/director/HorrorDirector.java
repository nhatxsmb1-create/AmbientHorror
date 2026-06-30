package dev.ambienthorror.director;

import dev.ambienthorror.AmbientHorror;
import dev.ambienthorror.config.ConfigManager;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;

public class HorrorDirector {

    private final AmbientHorror plugin;
    private final ConfigManager cfg;

    public HorrorDirector(AmbientHorror plugin) {
        this.plugin = plugin;
        this.cfg    = plugin.getConfigManager();
    }

    public double calculateScore(Player player) {
        double score = 0;
        score += getBiomeScore(player);
        score += getWeatherScore(player);
        score += getTimeScore(player);
        score += getNearbyPlayerScore(player);
        score += getPresenceBonus(player);

        double max = cfg.getDirectorConfig().getDouble("max-score", 100.0);
        double finalScore = Math.min(score, max);

        plugin.debug(String.format(
            "[Director] %s → B:%.1f W:%.1f T:%.1f N:%.1f P:%.1f = %.1f",
            player.getName(),
            getBiomeScore(player), getWeatherScore(player),
            getTimeScore(player), getNearbyPlayerScore(player),
            getPresenceBonus(player), finalScore
        ));

        return finalScore;
    }

    public boolean isScoreSufficient(Player player) {
        return calculateScore(player) >= cfg.getMinScoreThreshold();
    }

    private double getBiomeScore(Player player) {
        Biome biome = player.getLocation().getBlock().getBiome();
        FileConfiguration dir = cfg.getDirectorConfig();
        String biomeName = biome.name();
        if (dir.contains("biome-weights." + biomeName))
            return dir.getDouble("biome-weights." + biomeName, 0);
        return dir.getDouble("biome-weights.DEFAULT", 5);
    }

    private double getWeatherScore(Player player) {
        World world = player.getWorld();
        FileConfiguration dir = cfg.getDirectorConfig();
        if (world.isThundering()) return dir.getDouble("weather-weights.THUNDER", 20);
        if (world.hasStorm())     return dir.getDouble("weather-weights.RAIN", 12);
        return dir.getDouble("weather-weights.CLEAR", 0);
    }

    private double getTimeScore(Player player) {
        long time = player.getWorld().getTime();
        FileConfiguration dir = cfg.getDirectorConfig();
        for (String bracket : List.of("deep-night", "late-night", "early-night")) {
            String path = "time-weights." + bracket;
            long min = dir.getLong(path + ".min", -1);
            long max = dir.getLong(path + ".max", -1);
            if (min >= 0 && max >= 0 && time >= min && time <= max)
                return dir.getDouble(path + ".score", 0);
        }
        return 0;
    }

    private double getNearbyPlayerScore(Player player) {
        FileConfiguration dir = cfg.getDirectorConfig();
        int radius = cfg.getNearbyPlayerRadius();
        long nearbyCount = player.getWorld().getPlayers().stream()
                .filter(p -> !p.equals(player))
                .filter(p -> p.getLocation().distanceSquared(player.getLocation()) <= (radius * radius))
                .count();
        if (nearbyCount == 0) return dir.getDouble("nearby-player-weights.alone", 20);
        if (nearbyCount == 1) return dir.getDouble("nearby-player-weights.one", 10);
        if (nearbyCount == 2) return dir.getDouble("nearby-player-weights.two", 5);
        return dir.getDouble("nearby-player-weights.many", 0);
    }

    private double getPresenceBonus(Player player) {
        double presence = plugin.getPresenceManager().getPresence(player);
        double bonus = cfg.getDirectorConfig().getDouble("presence-bonus-per-level", 0.3);
        return presence * bonus;
    }
}
