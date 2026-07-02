package dev.ambienthorror.manager;

import dev.ambienthorror.AmbientHorror;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class ZoneManager {

    private final AmbientHorror plugin;

    public ZoneManager(AmbientHorror plugin) {
        this.plugin = plugin;
    }

    public String getZone(Player player) {
        FileConfiguration cfg = plugin.getConfigManager().getZoneConfig();
        Location loc = player.getLocation();

        double gzX = cfg.getDouble("ground-zero.center-x", 0);
        double gzZ = cfg.getDouble("ground-zero.center-z", 0);
        double gzRadius = cfg.getDouble("ground-zero.radius", 150);
        boolean gzUnlocked = cfg.getBoolean("ground-zero.unlocked", false);

        double distToGZ = Math.sqrt(
                Math.pow(loc.getX() - gzX, 2) +
                Math.pow(loc.getZ() - gzZ, 2)
        );

        if (gzUnlocked && distToGZ <= gzRadius) return "GROUND_ZERO";

        double dist = Math.sqrt(loc.getX() * loc.getX() + loc.getZ() * loc.getZ());
        double signalRadius = cfg.getDouble("signal.radius", 500);
        if (dist <= signalRadius) return "SIGNAL";

        return "DEAD";
    }

    public String getZoneDisplayName(String zone) {
        return switch (zone) {
            case "SIGNAL"      -> "§aSignal Zone";
            case "DEAD"        -> "§cDead Zone";
            case "GROUND_ZERO" -> "§4Ground Zero";
            default            -> "§7Unknown";
        };
    }

    public String getZoneColor(String zone) {
        return switch (zone) {
            case "SIGNAL"      -> "§a";
            case "DEAD"        -> "§c";
            case "GROUND_ZERO" -> "§4";
            default            -> "§7";
        };
    }
}
