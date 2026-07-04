package dev.ambienthorror.scheduler;

import dev.ambienthorror.AmbientHorror;
import dev.ambienthorror.events.AmbientEventData;
import dev.ambienthorror.events.EventRegistry;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

public class AmbientScheduler {

    private final AmbientHorror plugin;
    private BukkitTask task;

    public AmbientScheduler(AmbientHorror plugin) {
        this.plugin = plugin;
    }

    public void start() {
        int interval = plugin.getConfigManager().getSchedulerInterval();

        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!plugin.getConfigManager().isEnabled()) return;

            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (player.hasPermission("ambienthorror.bypass")) continue;
                if (!isWorldEnabled(player)) continue;

                // 1. Tick sanity
                plugin.getSanityManager().tickSanity(player);

                // 2. Director V2 tick
                plugin.getHorrorDirector().tick(player);

                // 3. Shadow tick
                plugin.getShadowManager().tickShadow(player);

                // 4. Ambient sound — chỉ ban đêm, không combat
                if (plugin.getConfigManager().isNightOnly() && !isNight(player)) continue;
                if (plugin.getCombatManager().isInCombat(player)) continue;
                if (!plugin.getHorrorDirector().isScoreSufficient(player)) continue;

                AmbientEventData event = EventRegistry.selectEvent(plugin, player);
                if (event == null) continue;

                if (!plugin.getCooldownManager().canTrigger(
                        player, event.key(), event.cooldownSeconds())) continue;

                plugin.getCooldownManager().markTriggered(player, event.key());
                plugin.getSoundManager().play(player, event.soundKey());

                plugin.debug("[Scheduler] Ambient " + event.key() +
                        " → " + player.getName());
            }

        }, interval, interval);

        plugin.log("AmbientScheduler V2 started (interval=" + interval + " ticks)");
    }

    public void stop() {
        if (task != null && !task.isCancelled()) task.cancel();
    }

    private boolean isWorldEnabled(Player player) {
        List<String> worlds = plugin.getConfigManager().getEnabledWorlds();
        if (worlds.isEmpty()) return true;
        return worlds.contains(player.getWorld().getName());
    }

    private boolean isNight(Player player) {
        long time = player.getWorld().getTime();
        return time >= 13000 && time <= 23000;
    }
}
