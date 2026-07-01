package dev.ambienthorror.config;

import dev.ambienthorror.AmbientHorror;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;

public class ConfigManager {

    private final AmbientHorror plugin;

    private FileConfiguration mainConfig;
    private FileConfiguration directorConfig;
    private FileConfiguration eventsConfig;
    private FileConfiguration soundsConfig;
    private FileConfiguration presenceConfig;
    private FileConfiguration shadowConfig;

    private File directorFile;
    private File eventsFile;
    private File soundsFile;
    private File presenceFile;
    private File shadowFile;

    public ConfigManager(AmbientHorror plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        mainConfig = plugin.getConfig();

        directorFile = saveResource("director.yml");
        directorConfig = YamlConfiguration.loadConfiguration(directorFile);

        eventsFile = saveResource("events.yml");
        eventsConfig = YamlConfiguration.loadConfiguration(eventsFile);

        soundsFile = saveResource("sounds.yml");
        soundsConfig = YamlConfiguration.loadConfiguration(soundsFile);

        presenceFile = saveResource("presence.yml");
        presenceConfig = YamlConfiguration.loadConfiguration(presenceFile);

        shadowFile = saveResource("shadow.yml");
        shadowConfig = YamlConfiguration.loadConfiguration(shadowFile);

        plugin.log("ConfigManager: tất cả configs đã load.");
    }

    public void reloadAll() {
        plugin.reloadConfig();
        mainConfig     = plugin.getConfig();
        directorConfig = YamlConfiguration.loadConfiguration(directorFile);
        eventsConfig   = YamlConfiguration.loadConfiguration(eventsFile);
        soundsConfig   = YamlConfiguration.loadConfiguration(soundsFile);
        presenceConfig = YamlConfiguration.loadConfiguration(presenceFile);
        shadowConfig   = YamlConfiguration.loadConfiguration(shadowFile);
        plugin.log("ConfigManager: reload hoàn tất.");
    }

    private File saveResource(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) plugin.saveResource(name, false);
        return file;
    }

    public FileConfiguration getMainConfig()     { return mainConfig; }
    public FileConfiguration getDirectorConfig() { return directorConfig; }
    public FileConfiguration getEventsConfig()   { return eventsConfig; }
    public FileConfiguration getSoundsConfig()   { return soundsConfig; }
    public FileConfiguration getPresenceConfig() { return presenceConfig; }
    public FileConfiguration getShadowConfig()   { return shadowConfig; }

    public boolean isEnabled()   { return mainConfig.getBoolean("enabled", true); }
    public boolean isDebug()     { return mainConfig.getBoolean("debug", false); }
    public int getNearbyPlayerRadius()   { return mainConfig.getInt("director.nearby-player-radius", 64); }
    public int getDirectorTickRate()     { return mainConfig.getInt("director.tick-rate", 100); }
    public double getMinScoreThreshold() { return mainConfig.getDouble("director.min-score-threshold", 15.0); }
    public int getSchedulerInterval()    { return mainConfig.getInt("scheduler.check-interval", 200); }
    public int getGlobalCooldownSeconds()    { return mainConfig.getInt("cooldown.global-seconds", 45); }
    public int getPerPlayerCooldownSeconds() { return mainConfig.getInt("cooldown.per-player-seconds", 90); }
    public boolean isNightOnly()         { return mainConfig.getBoolean("night-only", true); }
    public boolean isSkipInCombat()      { return mainConfig.getBoolean("skip-in-combat", true); }
    public int getCombatTimeoutSeconds() { return mainConfig.getInt("combat-timeout-seconds", 10); }
    public List<String> getEnabledWorlds() { return mainConfig.getStringList("enabled-worlds"); }
}
