package dev.ambienthorror.manager;

import dev.ambienthorror.AmbientHorror;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class SoundManager implements Listener {

    private final AmbientHorror plugin;
    private final Random random = new Random();

    // UUID của player đã load resource pack thành công
    private final Set<UUID> packLoaded = new HashSet<>();

    public SoundManager(AmbientHorror plugin) {
        this.plugin = plugin;
    }

    // ── Track resource pack status ────────────────────────────

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> {
                packLoaded.add(player.getUniqueId());
                plugin.debug("[SoundManager] " + player.getName() + " resource pack loaded ✓");
            }
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL -> {
                packLoaded.remove(player.getUniqueId());
                plugin.debug("[SoundManager] " + player.getName() + " resource pack FAILED → dùng fallback");
            }
            default -> {}
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        packLoaded.remove(event.getPlayer().getUniqueId());
    }

    // ── Main play method ─────────────────────────────────────

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

        // Quyết định phát custom hay fallback — KHÔNG phát cả 2
        boolean hasCustomPack = packLoaded.contains(player.getUniqueId());

        if (hasCustomPack) {
            // Player có resource pack → phát custom sound
            player.playSound(soundLoc, soundString, category, volume, pitch);
            plugin.debug("[Sound] CUSTOM " + player.getName() + " ← " + soundKey);
        } else {
            // Không có pack → phát vanilla fallback
            playFallback(player, soundLoc, fallback, volume, pitch, category);
            plugin.debug("[Sound] FALLBACK " + player.getName() + " ← " + soundKey);
        }
    }

    // ── Helpers ──────────────────────────────────────────────

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

    public boolean hasPackLoaded(Player player) {
        return packLoaded.contains(player.getUniqueId());
    }
}
