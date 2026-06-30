package dev.ambienthorror.manager;

import dev.ambienthorror.AmbientHorror;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Random;

public class SoundManager {

    private final AmbientHorror plugin;
    private final Random random = new Random();

    public SoundManager(AmbientHorror plugin) {
        this.plugin = plugin;
    }

    public void play(Player player, String soundKey) {
        ConfigurationSection sec = plugin.getConfigManager()
                .getSoundsConfig().getConfigurationSection("sounds." + soundKey);

        if (sec == null) {
            plugin.log("[SoundManager] Không tìm thấy sound key: " + soundKey);
            return;
        }

        String soundString = sec.getString("sound", "");
        String fallback    = sec.getString("fallback", "minecraft:entity.bat.ambient");
        float volume       = (float) sec.getDouble("volume", 0.5);
        float pitch        = (float) sec.getDouble("pitch", 1.0);
        boolean isPrivate  = sec.getBoolean("private", false);

        SoundCategory category;
        try {
            category = SoundCategory.valueOf(sec.getString("category", "AMBIENT"));
        } catch (IllegalArgumentException e) {
            category = SoundCategory.AMBIENT;
        }

        Location soundLoc;
        if (sec.contains("offset-behind")) {
            soundLoc = getBehindLocation(player, sec.getDouble("offset-behind", 3.0));
        } else if (sec.contains("min-distance")) {
            soundLoc = getRandomNearbyLocation(player,
                    sec.getInt("min-distance", 8),
                    sec.getInt("max-distance", 15));
        } else {
            soundLoc = player.getLocation();
        }

        if (isPrivate) {
            playPrivate(player, soundString, fallback, volume, pitch, category);
        } else {
            playAtLocation(player, soundLoc, soundString, fallback, volume, pitch, category);
        }

        plugin.debug("[Sound] " + player.getName() + " ← " + soundKey);
    }

    private void playPrivate(Player player, String sound, String fallback,
                             float volume, float pitch, SoundCategory category) {
        playFallback(player, player.getLocation(), fallback, volume, pitch, category);
        try {
            player.playSound(player.getLocation(), sound, category, volume, pitch);
        } catch (Exception ignored) {
        }
    }

    private void playAtLocation(Player player, Location loc, String sound, String fallback,
                                float volume, float pitch, SoundCategory category) {
        playFallback(player, loc, fallback, volume, pitch, category);
        try {
            player.playSound(loc, sound, category, volume, pitch);
        } catch (Exception ignored) {
        }
    }

    private void playFallback(Player player, Location loc, String fallbackStr,
                              float volume, float pitch, SoundCategory category) {
        try {
            String name = fallbackStr.replace("minecraft:", "")
                    .replace(".", "_").toUpperCase();
            Sound vanillaSound = Sound.valueOf(name);
            player.playSound(loc, vanillaSound, category, volume, pitch);
        } catch (Exception ex) {
            plugin.debug("[Sound] Fallback thất bại: " + fallbackStr);
        }
    }

    private Location getBehindLocation(Player player, double distance) {
        Location loc = player.getLocation().clone();
        double yaw = Math.toRadians(loc.getYaw() + 180);
        double dx = Math.sin(yaw) * distance;
        double dz = -Math.cos(yaw) * distance;
        return loc.add(dx, 0, dz);
    }

    private Location getRandomNearbyLocation(Player player, int minDist, int maxDist) {
        Location loc = player.getLocation().clone();
        double angle = random.nextDouble() * 2 * Math.PI;
        double dist = minDist + random.nextDouble() * (maxDist - minDist);
        return loc.add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
    }
}
