package dev.ambienthorror.manager;

import dev.ambienthorror.AmbientHorror;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatManager implements Listener {

    private final AmbientHorror plugin;
    private final Map<UUID, Long> lastCombatTime = new HashMap<>();

    public CombatManager(AmbientHorror plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim) markCombat(victim);
        if (event.getDamager() instanceof Player attacker) markCombat(attacker);
    }

    public boolean isInCombat(Player player) {
        if (!plugin.getConfigManager().isSkipInCombat()) return false;
        long timeout = plugin.getConfigManager().getCombatTimeoutSeconds() * 1000L;
        long last = lastCombatTime.getOrDefault(player.getUniqueId(), 0L);
        return (System.currentTimeMillis() - last) < timeout;
    }

    public void markCombat(Player player) {
        lastCombatTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void clearPlayer(Player player) {
        lastCombatTime.remove(player.getUniqueId());
    }
}
