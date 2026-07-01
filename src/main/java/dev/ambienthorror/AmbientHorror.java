package dev.ambienthorror;

import dev.ambienthorror.api.AmbientAPI;
import dev.ambienthorror.command.AmbientCommand;
import dev.ambienthorror.config.ConfigManager;
import dev.ambienthorror.director.HorrorDirector;
import dev.ambienthorror.manager.CombatManager;
import dev.ambienthorror.manager.CooldownManager;
import dev.ambienthorror.manager.PresenceManager;
import dev.ambienthorror.manager.SoundManager;
import dev.ambienthorror.scheduler.AmbientScheduler;
import org.bukkit.plugin.java.JavaPlugin;

public final class AmbientHorror extends JavaPlugin {

    private static AmbientHorror instance;

    private ConfigManager configManager;
    private HorrorDirector horrorDirector;
    private PresenceManager presenceManager;
    private CooldownManager cooldownManager;
    private CombatManager combatManager;
    private SoundManager soundManager;
    private AmbientScheduler ambientScheduler;
    private AmbientAPI ambientAPI;

    @Override
    public void onEnable() {
        instance = this;

        configManager = new ConfigManager(this);
        configManager.loadAll();

        presenceManager = new PresenceManager(this);
        cooldownManager = new CooldownManager(this);
        combatManager   = new CombatManager(this);
        soundManager    = new SoundManager(this);
        horrorDirector  = new HorrorDirector(this);

        ambientScheduler = new AmbientScheduler(this);
        ambientScheduler.start();

        getServer().getPluginManager().registerEvents(combatManager, this);
        getServer().getPluginManager().registerEvents(presenceManager, this);
        getServer().getPluginManager().registerEvents(soundManager, this);

        getCommand("ambienthorror").setExecutor(new AmbientCommand(this));
        getCommand("ambienthorror").setTabCompleter(new AmbientCommand(this));

        ambientAPI = new AmbientAPI(this);

        log("AmbientHorror V2 started.");
    }

    @Override
    public void onDisable() {
        if (ambientScheduler != null) ambientScheduler.stop();
        log("AmbientHorror disabled.");
        instance = null;
    }

    public static AmbientHorror getInstance() { return instance; }
    public ConfigManager getConfigManager()    { return configManager; }
    public HorrorDirector getHorrorDirector()  { return horrorDirector; }
    public PresenceManager getPresenceManager(){ return presenceManager; }
    public CooldownManager getCooldownManager(){ return cooldownManager; }
    public CombatManager getCombatManager()    { return combatManager; }
    public SoundManager getSoundManager()      { return soundManager; }
    public AmbientAPI getAmbientAPI()          { return ambientAPI; }

    public void log(String message) {
        getLogger().info(message);
    }

    public void debug(String message) {
        if (configManager != null && configManager.isDebug()) {
            getLogger().info("[DEBUG] " + message);
        }
    }
                                             }
