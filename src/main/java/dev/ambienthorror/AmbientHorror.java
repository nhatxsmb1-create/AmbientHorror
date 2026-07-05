package dev.ambienthorror;

import dev.ambienthorror.api.AmbientAPI;
import dev.ambienthorror.command.AmbientCommand;
import dev.ambienthorror.config.ConfigManager;
import dev.ambienthorror.director.HorrorDirector;
import dev.ambienthorror.manager.*;
import dev.ambienthorror.scheduler.AmbientScheduler;
import dev.ambienthorror.theman.TheManManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class AmbientHorror extends JavaPlugin {

    private static AmbientHorror instance;

    private ConfigManager configManager;
    private HorrorDirector horrorDirector;
    private SanityManager sanityManager;
    private SanityUI sanityUI;
    private ZoneManager zoneManager;
    private CooldownManager cooldownManager;
    private CombatManager combatManager;
    private SoundManager soundManager;
    private ShadowManager shadowManager;
    private TheManManager theManManager;
    private AmbientScheduler ambientScheduler;
    private AmbientAPI ambientAPI;

    @Override
    public void onEnable() {
        instance = this;

        configManager = new ConfigManager(this);
        configManager.loadAll();

        zoneManager     = new ZoneManager(this);
        sanityUI        = new SanityUI(this);
        sanityManager   = new SanityManager(this);
        cooldownManager = new CooldownManager(this);
        combatManager   = new CombatManager(this);
        soundManager    = new SoundManager(this);
        shadowManager   = new ShadowManager(this);
        theManManager   = new TheManManager(this);
        horrorDirector  = new HorrorDirector(this);

        ambientScheduler = new AmbientScheduler(this);
        ambientScheduler.start();
        theManManager.start();

        getServer().getPluginManager().registerEvents(combatManager, this);
        getServer().getPluginManager().registerEvents(sanityManager, this);
        getServer().getPluginManager().registerEvents(soundManager, this);
        getServer().getPluginManager().registerEvents(theManManager, this);

        getCommand("ambienthorror").setExecutor(new AmbientCommand(this));
        getCommand("ambienthorror").setTabCompleter(new AmbientCommand(this));

        ambientAPI = new AmbientAPI(this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new dev.ambienthorror.placeholder.AmbientHorrorExpansion(this).register();
            log("PlaceholderAPI expansion registered.");
        }

        log("The Last Broadcast — AmbientHorror started.");
    }

    @Override
    public void onDisable() {
        if (theManManager != null) theManManager.stop();
        if (shadowManager != null) shadowManager.cleanupAll();
        if (ambientScheduler != null) ambientScheduler.stop();
        getServer().getOnlinePlayers().forEach(p -> sanityUI.removePlayer(p));
        log("AmbientHorror disabled.");
        instance = null;
    }

    public static AmbientHorror getInstance() { return instance; }
    public ConfigManager getConfigManager()    { return configManager; }
    public HorrorDirector getHorrorDirector()  { return horrorDirector; }
    public SanityManager getSanityManager()    { return sanityManager; }
    public SanityUI getSanityUI()              { return sanityUI; }
    public ZoneManager getZoneManager()        { return zoneManager; }
    public CooldownManager getCooldownManager(){ return cooldownManager; }
    public CombatManager getCombatManager()    { return combatManager; }
    public SoundManager getSoundManager()      { return soundManager; }
    public ShadowManager getShadowManager()    { return shadowManager; }
    public TheManManager getTheManManager()    { return theManManager; }
    public AmbientAPI getAmbientAPI()          { return ambientAPI; }
    public SanityManager getPresenceManager()  { return sanityManager; }

    public void log(String message) { getLogger().info(message); }

    public void debug(String message) {
        if (configManager != null && configManager.isDebug()) {
            getLogger().info("[DEBUG] " + message);
        }
    }
}
