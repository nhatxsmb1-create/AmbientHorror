package dev.ambienthorror.director;

import dev.ambienthorror.AmbientHorror;
import org.bukkit.entity.Player;

public record DirectorContext(
        Player player,
        double sanity,
        int sanityTier,
        double directorScore,
        String zone,
        boolean isNight,
        boolean isDark,
        boolean isAlone,
        boolean isInCombat,
        int threatLevel,
        long timestamp
) {
    public static DirectorContext of(AmbientHorror plugin, Player player) {
        double sanity = plugin.getSanityManager().getSanity(player);
        int sanityTier = plugin.getSanityManager().getTierFromValue(sanity);
        double score = plugin.getHorrorDirector().calculateScore(player);
        String zone = plugin.getZoneManager().getZone(player);
        long time = player.getWorld().getTime();
        boolean isNight = time >= 13000 && time <= 23000;
        boolean isDark = player.getLocation().getBlock().getLightLevel() <= 7;
        int radius = plugin.getConfigManager().getNearbyPlayerRadius();
        boolean isAlone = player.getWorld().getPlayers().stream()
                .filter(p -> !p.equals(player))
                .noneMatch(p -> p.getLocation().distanceSquared(
                        player.getLocation()) <= (radius * radius));
        boolean isInCombat = plugin.getCombatManager().isInCombat(player);
        int threatLevel = getThreatLevel(plugin, player);

        return new DirectorContext(
                player, sanity, sanityTier, score, zone,
                isNight, isDark, isAlone, isInCombat,
                threatLevel, System.currentTimeMillis()
        );
    }

    private static int getThreatLevel(AmbientHorror plugin, Player player) {
        try {
            var wantedPlugin = plugin.getServer().getPluginManager()
                    .getPlugin("WantedPlugin");
            if (wantedPlugin == null) return 0;
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean isHighThreat()         { return threatLevel >= 3; }
    public boolean isCriticalSanity()     { return sanity < 20; }
    public boolean isDeadZone()           { return "DEAD".equals(zone); }
    public boolean isGroundZero()         { return "GROUND_ZERO".equals(zone); }
    public boolean isDangerousCondition() {
        return isNight && isAlone && (isDeadZone() || isGroundZero());
    }
}
