package dev.ambienthorror.manager;

import dev.ambienthorror.AmbientHorror;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class ShadowManager {

    private final AmbientHorror plugin;
    private final Map<UUID, Long> lastShadowTime = new HashMap<>();
    private final Map<UUID, ArmorStand> activeShadows = new HashMap<>();
    private final Map<UUID, BukkitTask> activeTasks = new HashMap<>();
    private static final Random RANDOM = new Random();

    public ShadowManager(AmbientHorror plugin) {
        this.plugin = plugin;
    }

    public void tickShadow(Player player) {
        FileConfiguration cfg = plugin.getConfigManager().getShadowConfig();
        double presence = plugin.getPresenceManager().getPresence(player);
        double minPresence = cfg.getDouble("min-presence", 60);
        if (presence < minPresence) return;

        int cooldown = cfg.getInt("cooldown-seconds", 120);
        long last = lastShadowTime.getOrDefault(player.getUniqueId(), 0L);
        if (System.currentTimeMillis() - last < cooldown * 1000L) return;

        if (activeShadows.containsKey(player.getUniqueId())) return;

        String type = selectShadowType(player, presence, cfg);
        if (type == null) return;

        lastShadowTime.put(player.getUniqueId(), System.currentTimeMillis());

        switch (type) {
            case "flicker"    -> triggerFlicker(player, cfg);
            case "peripheral" -> triggerPeripheral(player, cfg);
            case "behind"     -> triggerBehind(player, cfg);
        }

        plugin.debug("[Shadow] " + type.toUpperCase() + " → " + player.getName() +
                " (presence=" + String.format("%.1f", presence) + ")");
    }

    private void triggerFlicker(Player player, FileConfiguration cfg) {
        int duration  = cfg.getInt("flicker.duration-ticks", 8);
        int amplifier = cfg.getInt("flicker.amplifier", 1);
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.DARKNESS, duration, amplifier, false, false, false));
        plugin.getSoundManager().play(player, "shadow_flicker");
    }

    private void triggerPeripheral(Player player, FileConfiguration cfg) {
        double angle    = cfg.getDouble("peripheral.angle-degrees", 85);
        double distance = cfg.getDouble("peripheral.distance-blocks", 12);
        int despawnSec  = cfg.getInt("peripheral.despawn-seconds", 2);

        Location spawnLoc = getPeripheralLocation(player, angle, distance);
        if (spawnLoc == null) return;

        ArmorStand shadow = spawnShadowEntity(spawnLoc, cfg);
        if (shadow == null) return;

        activeShadows.put(player.getUniqueId(), shadow);

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> despawnShadow(player), despawnSec * 20L);
        activeTasks.put(player.getUniqueId(), task);
    }

    private void triggerBehind(Player player, FileConfiguration cfg) {
        double distance   = cfg.getDouble("behind.distance-blocks", 8);
        int followSeconds = cfg.getInt("behind.follow-seconds", 3);
        double followSpeed= cfg.getDouble("behind.follow-speed", 0.15);
        double despawnDist= cfg.getDouble("behind.despawn-distance", 3);

        Location spawnLoc = getBehindLocation(player, distance);
        ArmorStand shadow = spawnShadowEntity(spawnLoc, cfg);
        if (shadow == null) return;

        activeShadows.put(player.getUniqueId(), shadow);
        long[] tickCount = {0};
        long followStartTick = followSeconds * 20L;

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || shadow.isDead()) {
                despawnShadow(player);
                return;
            }
            tickCount[0]++;

            if (isPlayerLookingAt(player, shadow)) {
                plugin.debug("[Shadow] " + player.getName() + " nhìn vào shadow → despawn");
                despawnShadow(player);
                return;
            }

            if (tickCount[0] >= followStartTick) {
                double dist = shadow.getLocation().distance(player.getLocation());
                if (dist <= despawnDist) {
                    despawnShadow(player);
                    return;
                }
                Vector dir = player.getLocation().toVector()
                        .subtract(shadow.getLocation().toVector())
                        .normalize().multiply(followSpeed);
                shadow.setVelocity(dir);
            }
        }, 1L, 1L);

        activeTasks.put(player.getUniqueId(), task);
    }

    private ArmorStand spawnShadowEntity(Location loc, FileConfiguration cfg) {
        if (loc == null || loc.getWorld() == null) return null;
        try {
            return loc.getWorld().spawn(loc, ArmorStand.class, e -> {
                e.setInvisible(cfg.getBoolean("entity.invisible", true));
                e.setSilent(cfg.getBoolean("entity.silent", true));
                e.setCustomNameVisible(cfg.getBoolean("entity.name-visible", false));
                e.setCustomName(cfg.getString("entity.name", "shadow_entity"));
                e.setGravity(false);
                e.setInvulnerable(true);
                e.setPersistent(false);
            });
        } catch (Exception e) {
            plugin.log("[ShadowManager] Lỗi spawn: " + e.getMessage());
            return null;
        }
    }

    public void despawnShadow(Player player) {
        BukkitTask task = activeTasks.remove(player.getUniqueId());
        if (task != null && !task.isCancelled()) task.cancel();
        ArmorStand shadow = activeShadows.remove(player.getUniqueId());
        if (shadow != null && !shadow.isDead()) shadow.remove();
    }

    public void cleanupPlayer(Player player) {
        despawnShadow(player);
        lastShadowTime.remove(player.getUniqueId());
    }

    public void cleanupAll() {
        activeShadows.values().stream()
                .filter(s -> s != null && !s.isDead())
                .forEach(Entity::remove);
        activeTasks.values().stream()
                .filter(t -> t != null && !t.isCancelled())
                .forEach(BukkitTask::cancel);
        activeShadows.clear();
        activeTasks.clear();
        lastShadowTime.clear();
    }

    private Location getPeripheralLocation(Player player, double angleDeg, double distance) {
        Location loc = player.getLocation().clone();
        double yaw = Math.toRadians(loc.getYaw());
        double side = RANDOM.nextBoolean() ? 1 : -1;
        double finalAngle = yaw + Math.toRadians(angleDeg) * side;
        return loc.add(-Math.sin(finalAngle) * distance, 0, Math.cos(finalAngle) * distance);
    }

    private Location getBehindLocation(Player player, double distance) {
        Location loc = player.getLocation().clone();
        double yaw = Math.toRadians(loc.getYaw() + 180);
        return loc.add(Math.sin(yaw) * distance, 0, -Math.cos(yaw) * distance);
    }

    private boolean isPlayerLookingAt(Player player, Entity entity) {
        Vector toEntity = entity.getLocation().toVector()
                .subtract(player.getEyeLocation().toVector()).normalize();
        return toEntity.dot(player.getEyeLocation().getDirection().normalize()) > 0.7;
    }

    private String selectShadowType(Player player, double presence, FileConfiguration cfg) {
        List<String[]> eligible = new ArrayList<>();
        if (cfg.getBoolean("flicker.enabled", true)
                && presence >= cfg.getDouble("flicker.min-presence", 60))
            eligible.add(new String[]{"flicker", String.valueOf(cfg.getInt("flicker.weight", 40))});
        if (cfg.getBoolean("peripheral.enabled", true)
                && presence >= cfg.getDouble("peripheral.min-presence", 65))
            eligible.add(new String[]{"peripheral", String.valueOf(cfg.getInt("peripheral.weight", 30))});
        if (cfg.getBoolean("behind.enabled", true)
                && presence >= cfg.getDouble("behind.min-presence", 70))
            eligible.add(new String[]{"behind", String.valueOf(cfg.getInt("behind.weight", 20))});
        if (eligible.isEmpty()) return null;

        int total = eligible.stream().mapToInt(e -> Integer.parseInt(e[1])).sum();
        int roll = RANDOM.nextInt(total);
        int cum = 0;
        for (String[] entry : eligible) {
            cum += Integer.parseInt(entry[1]);
            if (roll < cum) return entry[0];
        }
        return eligible.get(0)[0];
    }
    }
