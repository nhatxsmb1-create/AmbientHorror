package dev.ambienthorror.theman;

import dev.ambienthorror.AmbientHorror;
import dev.ambienthorror.theman.TheMan.Phase;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class TheManManager implements Listener {

    private final AmbientHorror plugin;
    private final Map<UUID, TheMan> activeInstances = new HashMap<>();
    private BukkitTask tickTask;
    private final Random random = new Random();

    public TheManManager(AmbientHorror plugin) {
        this.plugin = plugin;
    }

    public void start() {
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, TheMan> entry : new HashMap<>(activeInstances).entrySet()) {
                TheMan man = entry.getValue();
                if (!man.isActive()) {
                    activeInstances.remove(entry.getKey());
                    continue;
                }
                man.tick();
            }
        }, 2L, 2L);
    }

    public void stop() {
        if (tickTask != null && !tickTask.isCancelled()) tickTask.cancel();
        cleanupAll();
    }

    public void spawnForPlayer(Player player) {
        if (activeInstances.containsKey(player.getUniqueId())) return;
        double sanity = plugin.getSanityManager().getSanity(player);
        Phase phase = getPhaseFromSanity(sanity);
        Location spawnLoc = getSpawnLocation(player, phase);
        if (spawnLoc == null) return;

        TheMan man = new TheMan(plugin, player);
        if (man.spawn(spawnLoc, phase)) {
            activeInstances.put(player.getUniqueId(), man);
        }
    }

    public void forceSpawn(Player player, Phase phase) {
        despawnForPlayer(player);
        Location spawnLoc = getSpawnLocation(player, phase);
        if (spawnLoc == null) return;
        TheMan man = new TheMan(plugin, player);
        if (man.spawn(spawnLoc, phase)) {
            activeInstances.put(player.getUniqueId(), man);
        }
    }

    public void despawnForPlayer(Player player) {
        TheMan man = activeInstances.remove(player.getUniqueId());
        if (man != null) man.despawn();
    }

    public void cleanupAll() {
        for (TheMan man : activeInstances.values()) man.despawn();
        activeInstances.clear();
    }

    public void updatePhase(Player player) {
        TheMan man = activeInstances.get(player.getUniqueId());
        if (man == null || !man.isActive()) return;
        double sanity = plugin.getSanityManager().getSanity(player);
        Phase newPhase = getPhaseFromSanity(sanity);
        if (newPhase != man.getPhase()) man.setPhase(newPhase);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        despawnForPlayer(event.getPlayer());
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        for (TheMan man : activeInstances.values()) {
            if (man.getBaseEntity() != null &&
                man.getBaseEntity().equals(event.getEntity())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        for (TheMan man : activeInstances.values()) {
            if (man.getBaseEntity() != null &&
                man.getBaseEntity().equals(event.getEntity())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        // Không cho The Man chết
        for (TheMan man : activeInstances.values()) {
            if (man.getBaseEntity() != null &&
                man.getBaseEntity().equals(event.getEntity())) {
                event.getDrops().clear();
                event.setDroppedExp(0);
                return;
            }
        }
    }

    private Phase getPhaseFromSanity(double sanity) {
        if (sanity <= 5)  return Phase.PHASE_3;
        if (sanity <= 10) return Phase.PHASE_2;
        return Phase.PHASE_1;
    }

    private Location getSpawnLocation(Player player, Phase phase) {
        int minDist, maxDist;
        switch (phase) {
            case PHASE_1 -> { minDist = 60; maxDist = 100; }
            case PHASE_2 -> { minDist = 30; maxDist = 50; }
            case PHASE_3 -> { minDist = 10; maxDist = 20; }
            default      -> { minDist = 50; maxDist = 80; }
        }

        Location playerLoc = player.getLocation();
        
        // Thử spawn tối đa 10 lần để tìm location safe
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double dist = minDist + random.nextDouble() * (maxDist - minDist);
            double dx = Math.cos(angle) * dist;
            double dz = Math.sin(angle) * dist;

            int spawnX = (int)(playerLoc.getX() + dx);
            int spawnZ = (int)(playerLoc.getZ() + dz);
            
            // Check world border
            if (!playerLoc.getWorld().getWorldBorder().isInside(
                    new Location(playerLoc.getWorld(), spawnX, playerLoc.getY(), spawnZ))) {
                continue;
            }

            // Spawn đúng trên mặt đất + check collision
            Block highestBlock = playerLoc.getWorld().getHighestBlockAt(spawnX, spawnZ);
            Location spawnLoc = highestBlock.getLocation().add(0.5, 1, 0.5);
            
            // Check xung quanh có block chắn không (3x3x2)
            if (isSafeLocation(spawnLoc)) {
                plugin.debug("[TheMan] Spawn location found (attempt " + (attempt + 1) + ") at " + 
                    String.format("(%.0f, %.0f, %.0f)", spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ()));
                return spawnLoc;
            }
        }
        
        plugin.debug("[TheMan] Không tìm được safe spawn location sau 10 lần thử!");
        return null;
    }

    /**
     * Kiểm tra location có safe để spawn không (không bị kẹt block)
     */
    private boolean isSafeLocation(Location loc) {
        Block center = loc.getBlock();
        Block above = center.getRelative(0, 1, 0);
        
        // Center và block trên phải rỗng
        if (!center.isEmpty() && !isPassable(center)) return false;
        if (!above.isEmpty() && !isPassable(above)) return false;
        
        // Check 3x3 xung quanh - ít nhất không được solid
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block relative = center.getRelative(x, 0, z);
                if (!relative.isEmpty() && !isPassable(relative)) {
                    return false;
                }
            }
        }
        
        return true;
    }

    /**
     * Check block có passable không (air, liquid, tall grass, etc.)
     */
    private boolean isPassable(Block block) {
        Material mat = block.getType();
        if (mat == null || mat == Material.AIR) return true;
        return mat.isTransparent() || mat == Material.WATER || mat == Material.LAVA || 
               mat.name().contains("GRASS") || mat.name().contains("FLOWER") ||
               mat.name().contains("CROP") || mat.name().contains("VINE");
    }

    public boolean isActive(Player player) {
        TheMan man = activeInstances.get(player.getUniqueId());
        return man != null && man.isActive();
    }

    public TheMan getInstance(Player player) {
        return activeInstances.get(player.getUniqueId());
    }
}
