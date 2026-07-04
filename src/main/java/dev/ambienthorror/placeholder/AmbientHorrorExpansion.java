package dev.ambienthorror.placeholder;

import dev.ambienthorror.AmbientHorror;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AmbientHorrorExpansion extends PlaceholderExpansion {

    private final AmbientHorror plugin;

    public AmbientHorrorExpansion(AmbientHorror plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "ambienthorror"; }
    @Override public @NotNull String getAuthor()     { return "AmbientHorror"; }
    @Override public @NotNull String getVersion()    { return plugin.getDescription().getVersion(); }
    @Override public boolean persist()               { return true; }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        return switch (params.toLowerCase()) {
            case "sanity" ->
                String.format("%.1f", plugin.getSanityManager().getSanity(player));
            case "sanity_int" ->
                String.valueOf((int) plugin.getSanityManager().getSanity(player));
            case "sanity_tier" ->
                String.valueOf(plugin.getSanityManager().getPresenceTier(player));
            case "sanity_name" -> {
                int tier = plugin.getSanityManager().getPresenceTier(player);
                yield plugin.getSanityManager().getTierName(tier);
            }
            case "sanity_bar" -> {
                double sanity = plugin.getSanityManager().getSanity(player);
                yield buildBar(sanity);
            }
            case "sanity_color" -> {
                int tier = plugin.getSanityManager().getPresenceTier(player);
                yield switch (tier) {
                    case 0 -> "§a";
                    case 1 -> "§e";
                    case 2 -> "§c";
                    case 3 -> "§5";
                    default -> "§f";
                };
            }
            case "zone" ->
                plugin.getZoneManager().getZone(player);
            case "zone_display" -> {
                String zone = plugin.getZoneManager().getZone(player);
                yield plugin.getZoneManager().getZoneDisplayName(zone);
            }
            case "zone_color" -> {
                String zone = plugin.getZoneManager().getZone(player);
                yield plugin.getZoneManager().getZoneColor(zone);
            }
            case "director_score" ->
                String.format("%.1f", plugin.getHorrorDirector().calculateScore(player));
            case "is_night" -> {
                long time = player.getWorld().getTime();
                yield String.valueOf(time >= 13000 && time <= 23000);
            }
            case "is_combat" ->
                String.valueOf(plugin.getCombatManager().isInCombat(player));
            default -> null;
        };
    }

    private String buildBar(double sanity) {
        int filled = (int) Math.round(sanity / 10.0);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 10; i++) sb.append(i < filled ? "█" : "░");
        sb.append("]");
        return sb.toString();
    }
                  }
