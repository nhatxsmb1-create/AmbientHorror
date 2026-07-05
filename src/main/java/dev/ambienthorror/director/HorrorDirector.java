package dev.ambienthorror.director;

import dev.ambienthorror.AmbientHorror;
import dev.ambienthorror.theman.TheMan.Phase;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class HorrorDirector {

    private final AmbientHorror plugin;
    private final DirectorMemory memory;
    private final Random random = new Random();

    private static final String[] CORRUPTED_BROADCASTS = {
        "HELP",
        "HELP HELP HELP",
        "Đừng đến Ground Zero",
        "Thí nghiệm đã thoát khỏi tầm kiểm soát",
        "Tần số 108.8 — Đừng nghe",
        "Họ biết mày ở đâu",
        "3:17",
        "Tín hiệu bị gián đoạn ▒▒▒",
        "Experiment-07 đã được kích hoạt",
        "Còn bao nhiêu người trong khu vực?",
        "Đừng tin vào bất kỳ ai",
        "▒▒▒ không còn ai ▒▒▒"
    };

    public HorrorDirector(AmbientHorror plugin) {
        this.plugin = plugin;
        this.memory = new DirectorMemory();
    }

    public void tick(Player player) {
        DirectorContext ctx = DirectorContext.of(plugin, player);
        if (ctx.directorScore() < plugin.getConfigManager().getMinScoreThreshold()) return;

        List<DirectorAction> actions = selectActions(ctx);
        for (DirectorAction action : actions) {
            executeAction(action, ctx);
        }
    }

    private List<DirectorAction> selectActions(DirectorContext ctx) {
        List<DirectorAction> actions = new ArrayList<>();
        UUID uuid = ctx.player().getUniqueId();

        if (ctx.isNight() && !ctx.isInCombat() &&
            memory.canTrigger(uuid, DirectorAction.AMBIENT_SOUND)) {
            actions.add(DirectorAction.AMBIENT_SOUND);
        }

        if (ctx.sanity() < 70 && ctx.isNight()) {
            if (ctx.sanity() >= 60 &&
                memory.canTrigger(uuid, DirectorAction.SHADOW_FLICKER)) {
                if (random.nextInt(100) < getShadowChance(ctx))
                    actions.add(DirectorAction.SHADOW_FLICKER);
            } else if (ctx.sanity() >= 40 &&
                       memory.canTrigger(uuid, DirectorAction.SHADOW_PERIPHERAL)) {
                if (random.nextInt(100) < getShadowChance(ctx))
                    actions.add(DirectorAction.SHADOW_PERIPHERAL);
            } else if (ctx.sanity() < 40 &&
                       memory.canTrigger(uuid, DirectorAction.SHADOW_BEHIND)) {
                if (random.nextInt(100) < getShadowChance(ctx))
                    actions.add(DirectorAction.SHADOW_BEHIND);
            }
        }

        if (ctx.isDangerousCondition() && ctx.sanity() < 50 &&
            memory.canTrigger(uuid, DirectorAction.RADIO_CORRUPTED)) {
            if (random.nextInt(100) < getCorruptedRadioChance(ctx))
                actions.add(DirectorAction.RADIO_CORRUPTED);
        }

        if (ctx.isGroundZero() || (ctx.isCriticalSanity() && ctx.isAlone())) {
            if (memory.canTrigger(uuid, DirectorAction.SANITY_DRAIN))
                actions.add(DirectorAction.SANITY_DRAIN);
        }

        // The Man — sanity < 20, đêm, một mình
        if (ctx.sanity() < 20 && ctx.isNight() && ctx.isAlone() &&
            !plugin.getTheManManager().isActive(ctx.player()) &&
            memory.canTrigger(uuid, DirectorAction.THE_MAN_HINT)) {
            if (random.nextInt(100) < 20)
                actions.add(DirectorAction.THE_MAN_HINT);
        }

        return actions;
    }

    private void executeAction(DirectorAction action, DirectorContext ctx) {
        Player player = ctx.player();
        UUID uuid = player.getUniqueId();

        switch (action) {
            case AMBIENT_SOUND -> memory.markTriggered(uuid, action);

            case SHADOW_FLICKER -> {
                plugin.getShadowManager().triggerSpecific(player, "flicker");
                memory.markTriggered(uuid, action);
                plugin.debug("[Director] SHADOW_FLICKER → " + player.getName());
            }
            case SHADOW_PERIPHERAL -> {
                plugin.getShadowManager().triggerSpecific(player, "peripheral");
                memory.markTriggered(uuid, action);
                plugin.debug("[Director] SHADOW_PERIPHERAL → " + player.getName());
            }
            case SHADOW_BEHIND -> {
                plugin.getShadowManager().triggerSpecific(player, "behind");
                memory.markTriggered(uuid, action);
                plugin.debug("[Director] SHADOW_BEHIND → " + player.getName());
            }
            case RADIO_CORRUPTED -> {
                String msg = CORRUPTED_BROADCASTS[random.nextInt(CORRUPTED_BROADCASTS.length)];
                sendCorruptedBroadcast(msg);
                memory.markTriggered(uuid, action);
                plugin.debug("[Director] RADIO_CORRUPTED: " + msg);
            }
            case SANITY_DRAIN -> {
                plugin.getSanityManager().reduceSanity(player, 5.0);
                memory.markTriggered(uuid, action);
                plugin.debug("[Director] SANITY_DRAIN → " + player.getName());
            }
            case THE_MAN_HINT -> {
                // Spawn The Man thật sự
                plugin.getTheManManager().spawnForPlayer(player);
                memory.markTriggered(uuid, action);
                plugin.debug("[Director] THE_MAN_SPAWN → " + player.getName());
            }
            default -> {}
        }
    }

    public double calculateScore(Player player) {
        double score = 0;
        score += getBiomeScore(player);
        score += getWeatherScore(player);
        score += getTimeScore(player);
        score += getNearbyPlayerScore(player);
        score += getPresenceBonus(player);
        double max = plugin.getConfigManager().getDirectorConfig()
                .getDouble("max-score", 100.0);
        return Math.min(score, max);
    }

    public boolean isScoreSufficient(Player player) {
        return calculateScore(player) >= plugin.getConfigManager().getMinScoreThreshold();
    }

    private int getShadowChance(DirectorContext ctx) {
        int base = 20;
        if (ctx.isDeadZone())       base += 15;
        if (ctx.isGroundZero())     base += 30;
        if (ctx.isAlone())          base += 10;
        if (ctx.isCriticalSanity()) base += 20;
        return Math.min(base, 80);
    }

    private int getCorruptedRadioChance(DirectorContext ctx) {
        int base = 10;
        if (ctx.isGroundZero())     base += 20;
        if (ctx.isCriticalSanity()) base += 15;
        if (ctx.isHighThreat())     base += 10;
        return Math.min(base, 50);
    }

    private void sendCorruptedBroadcast(String message) {
        try {
            var tlbRadio = plugin.getServer().getPluginManager().getPlugin("TLBRadio");
            if (tlbRadio != null) {
                var getRadioManager = tlbRadio.getClass().getMethod("getRadioManager");
                var radioManager = getRadioManager.invoke(tlbRadio);
                var adminBroadcast = radioManager.getClass().getMethod(
                        "adminBroadcast", String.class, String.class, String.class);
                adminBroadcast.invoke(radioManager, "static", message, "DIRECTOR");
                return;
            }
        } catch (Exception e) {
            plugin.debug("[Director] TLBRadio hook failed: " + e.getMessage());
        }
        for (Player p : plugin.getServer().getOnlinePlayers())
            p.sendMessage("§8[▒▒▒] §7" + message);
    }

    private double getBiomeScore(Player player) {
        Biome biome = player.getLocation().getBlock().getBiome();
        FileConfiguration dir = plugin.getConfigManager().getDirectorConfig();
        String biomeName = biome.name();
        if (dir.contains("biome-weights." + biomeName))
            return dir.getDouble("biome-weights." + biomeName, 0);
        return dir.getDouble("biome-weights.DEFAULT", 5);
    }

    private double getWeatherScore(Player player) {
        World world = player.getWorld();
        FileConfiguration dir = plugin.getConfigManager().getDirectorConfig();
        if (world.isThundering()) return dir.getDouble("weather-weights.THUNDER", 20);
        if (world.hasStorm())     return dir.getDouble("weather-weights.RAIN", 12);
        return dir.getDouble("weather-weights.CLEAR", 0);
    }

    private double getTimeScore(Player player) {
        long time = player.getWorld().getTime();
        FileConfiguration dir = plugin.getConfigManager().getDirectorConfig();
        for (String bracket : List.of("deep-night", "late-night", "early-night")) {
            String path = "time-weights." + bracket;
            long min = dir.getLong(path + ".min", -1);
            long max = dir.getLong(path + ".max", -1);
            if (min >= 0 && max >= 0 && time >= min && time <= max)
                return dir.getDouble(path + ".score", 0);
        }
        return 0;
    }

    private double getNearbyPlayerScore(Player player) {
        FileConfiguration dir = plugin.getConfigManager().getDirectorConfig();
        int radius = plugin.getConfigManager().getNearbyPlayerRadius();
        long nearbyCount = player.getWorld().getPlayers().stream()
                .filter(p -> !p.equals(player))
                .filter(p -> p.getLocation().distanceSquared(
                        player.getLocation()) <= (radius * radius))
                .count();
        if (nearbyCount == 0) return dir.getDouble("nearby-player-weights.alone", 20);
        if (nearbyCount == 1) return dir.getDouble("nearby-player-weights.one", 10);
        if (nearbyCount == 2) return dir.getDouble("nearby-player-weights.two", 5);
        return dir.getDouble("nearby-player-weights.many", 0);
    }

    private double getPresenceBonus(Player player) {
        double sanity = plugin.getSanityManager().getSanity(player);
        double presence = 100 - sanity;
        double bonus = plugin.getConfigManager().getDirectorConfig()
                .getDouble("presence-bonus-per-level", 0.3);
        return presence * bonus;
    }

    public DirectorMemory getMemory() { return memory; }
        }
