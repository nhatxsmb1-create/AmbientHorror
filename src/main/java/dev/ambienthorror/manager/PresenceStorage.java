package dev.ambienthorror.manager;

import dev.ambienthorror.AmbientHorror;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class PresenceStorage {

    private final AmbientHorror plugin;
    private final File dataFile;
    private FileConfiguration data;

    public PresenceStorage(AmbientHorror plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data/presence_data.yml");
        load();
    }

    public void load() {
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.log("[PresenceStorage] Không thể tạo file: " + e.getMessage());
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void save() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.log("[PresenceStorage] Lỗi lưu file: " + e.getMessage());
        }
    }

    public double loadPresence(UUID uuid) {
        String path = "players." + uuid;
        if (!data.contains(path)) return 0.0;

        double savedPresence = data.getDouble(path + ".presence", 0.0);
        long lastOnline = data.getLong(path + ".last-online", System.currentTimeMillis());
        return calculateOfflineDecay(savedPresence, lastOnline);
    }

    public void savePresence(UUID uuid, String playerName, double presence) {
        String path = "players." + uuid;
        data.set(path + ".presence", Math.round(presence * 10.0) / 10.0);
        data.set(path + ".last-online", System.currentTimeMillis());
        data.set(path + ".name", playerName);
        save();
        plugin.debug("[PresenceStorage] Saved " + playerName + ": " +
                String.format("%.1f", presence));
    }

    private double calculateOfflineDecay(double presence, long lastOnlineMillis) {
        FileConfiguration cfg = plugin.getConfigManager().getPresenceConfig();
        double decayPerMinute = cfg.getDouble("offline-decay-per-minute", 0.5);
        double floor = cfg.getDouble("offline-decay-floor", 10.0);

        long now = System.currentTimeMillis();
        double offlineMinutes = (now - lastOnlineMillis) / 60000.0;
        double result = presence - (offlineMinutes * decayPerMinute);

        if (presence >= floor) {
            result = Math.max(result, floor);
        } else {
            result = Math.max(result, 0.0);
        }

        return Math.min(result, 100.0);
    }
}
