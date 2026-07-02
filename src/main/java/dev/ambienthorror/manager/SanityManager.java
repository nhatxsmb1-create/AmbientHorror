package dev.ambienthorror.manager;

import dev.ambienthorror.AmbientHorror;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SanityManager implements Listener {

    private final AmbientHorror plugin;
    private final PresenceStorage storage;

    private final Map<UUID, Double> sanityMap = new HashMap<>();
    private final Map<UUID, Location> lastLocation = new HashMap<>();
    private final Map<UUID, Integer> lastTier = new HashMap<>();

    public SanityManager(AmbientHorror plugin) {
        this.plugin = plugin;
        this.storage = new PresenceStorage(plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        double saved = storage.loadPresence(player.getUniqueId());
        double sanity = saved == 0.0 ? 100.0 : saved;
        sanityMap.put(player.getUniqueId(), sanity);
        lastTier.put(player.getUniqueId(), getTierFromValue(sanity));
        plugin.debug("[Sanity] " + player.getName() + " joined → " +
                String.format("%.1f", sanity));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        storage.savePresence(player.getUniqueId(), player.getName(), getSanity(player));
        sanityMap.remove(player.getUniqueId());
        lastLocation.remove(player.getUniqueId());
        lastTier.remove(player.getUniqueId());
        plugin.getShadowManager().cleanupPlayer(player);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        dead.getWorld().getPlayers().stream()
                .filter(p -> !p.equals(dead))
                .filter(p -> p.getLocation().distanceSquared(dead.getLocation()) <= 400)
                .forEach(witness -> {
                    reduceSanity(witness, 8.0);
                    plugin.debug("[Sanity] " + witness.getName() +
                            " chứng kiến " + dead.getName() + " chết → -8");
                });
    }

    public void tickSanity(Player player) {
        FileConfiguration cfg = plugin.getConfigManager().getPresenceConfig();
        double current = getSanity(player);
        double delta = 0;

        boolean isNight   = isNight(player);
        boolean isDark    = isDark(player, cfg);
        boolean isStill   = isStandingStill(player);
        boolean isIndoors = isIndoors(player, cfg);
        boolean isCrowded = isCrowded(player);
        boolean isAlone   = isAlone(player);
        String zone = plugin.getZoneManager().getZone(player);

        if ("DEAD".equals(zone))         delta -= 1.2;
        if ("GROUND_ZERO".equals(zone))  delta -= 2.5;
        if (isNight && isAlone)          delta -= 0.6;
        if (isDark)                      delta -= 0.5;
        if (isStill && isNight)          delta -= 0.3;

        if ("SIGNAL".equals(zone))       delta += 0.8;
        if (isCrowded)                   delta += 0.5;
        if (isIndoors && !isNight)       delta += 0.3;
        if (!isNight && "SIGNAL".equals(zone)) delta += 0.4;

        double newSanity = Math.max(0, Math.min(100, current + delta));
        sanityMap.put(player.getUniqueId(), newSanity);
        lastLocation.put(player.getUniqueId(), player.getLocation().clone());

        plugin.getSanityUI().update(player, newSanity);
        checkTierChange(player, current, newSanity);

        plugin.debug(String.format(
            "[Sanity] %s %.1f→%.1f (Δ%.2f) zone=%s night=%b dark=%b alone=%b",
            player.getName(), current, newSanity, delta, zone, isNight, isDark, isAlone
        ));
    }

    public double getSanity(Player player) {
        return sanityMap.getOrDefault(player.getUniqueId(), 100.0);
    }

    public void setSanity(Player player, double value) {
        double clamped = Math.max(0, Math.min(100, value));
        sanityMap.put(player.getUniqueId(), clamped);
        plugin.getSanityUI().update(player, clamped);
    }

    public void reduceSanity(Player player, double amount) {
        setSanity(player, getSanity(player) - amount);
    }

    public void restoreSanity(Player player, double amount) {
        setSanity(player, getSanity(player) + amount);
    }

    public void setPresence(Player player, double value) { setSanity(player, 100 - value); }
    public void addPresence(Player player, double amount) { reduceSanity(player, amount); }
    public void reducePresence(Player player, double amount) { restoreSanity(player, amount); }
    public double getPresence(Player player) { return 100 - getSanity(player); }
    public int getPresenceTier(Player player) { return getTierFromValue(getSanity(player)); }

    public int getTierFromValue(double sanity) {
        if (sanity >= 70) return 0;
        if (sanity >= 40) return 1;
        if (sanity >= 20) return 2;
        return 3;
    }

    public String getTierName(int tier) {
        return switch (tier) {
            case 0 -> "Tỉnh táo";
            case 1 -> "Lo lắng";
            case 2 -> "Hoang mang";
            case 3 -> "Mất kiểm soát";
            default -> "Unknown";
        };
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
        return player.getLocation().distanceSquared(prev) <= 1.0;
    }

    private boolean isIndoors(Player player, FileConfiguration cfg) {
        int height = cfg.getInt("indoors-check-height", 5);
        Location loc = player.getLocation().clone();
        for (int i = 1; i <= height; i++) {
            Block above = loc.clone().add(0, i, 0).getBlock();
            if (above.getType().isSolid() && !above.getType().name().contains("LEAVES"))
                return true;
        }
        return false;
    }

    private boolean isCrowded(Player player) {
        int radius = plugin.getConfigManager().getNearbyPlayerRadius();
        return player.getWorld().getPlayers().stream()
                .filter(p -> !p.equals(player))
                .filter(p -> p.getLocation().distanceSquared(player.getLocation()) <= (radius * radius))
                .count() >= 2;
    }

    private boolean isAlone(Player player) {
        int radius = plugin.getConfigManager().getNearbyPlayerRadius();
        return player.getWorld().getPlayers().stream()
                .filter(p -> !p.equals(player))
                .noneMatch(p -> p.getLocation().distanceSquared(player.getLocation()) <= (radius * radius));
    }

    private void checkTierChange(Player player, double oldSanity, double newSanity) {
        int oldTier = getTierFromValue(oldSanity);
        int newTier = getTierFromValue(newSanity);
        if (newTier == oldTier) return;
        lastTier.put(player.getUniqueId(), newTier);
        if (newTier > oldTier) {
            plugin.debug("[Sanity] " + player.getName() + " tier DOWN: " +
                    oldTier + "→" + newTier + " (" + getTierName(newTier) + ")");
            plugin.getSanityUI().onTierChange(player, newTier);
        } else {
            plugin.debug("[Sanity] " + player.getName() + " tier UP: " +
                    oldTier + "→" + newTier + " (" + getTierName(newTier) + ")");
        }
    }

    public void clearSanity(Player player) {
        sanityMap.remove(player.getUniqueId());
    }
}
