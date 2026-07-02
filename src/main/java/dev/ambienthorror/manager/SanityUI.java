package dev.ambienthorror.manager;

import dev.ambienthorror.AmbientHorror;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SanityUI {

    private final AmbientHorror plugin;
    private final Map<UUID, BossBar> bossBars = new HashMap<>();

    public SanityUI(AmbientHorror plugin) {
        this.plugin = plugin;
    }

    public void update(Player player, double sanity) {
        updateActionBar(player, sanity);
        updateBossBar(player, sanity);
        if (sanity < 20 && Math.random() < 0.05) {
            flashTitle(player);
        }
    }

    public void onTierChange(Player player, int newTier) {
        String tierName = plugin.getSanityManager().getTierName(newTier);
        Title title = Title.title(
                Component.text(""),
                Component.text(tierName)
                        .color(getTierColor(newTier))
                        .decorate(TextDecoration.ITALIC),
                Title.Times.times(
                        Duration.ofMillis(500),
                        Duration.ofMillis(1500),
                        Duration.ofMillis(500)
                )
        );
        player.showTitle(title);
    }

    public void removePlayer(Player player) {
        BossBar bar = bossBars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }

    private void updateActionBar(Player player, double sanity) {
        int tier = plugin.getSanityManager().getTierFromValue(sanity);
        String tierName = plugin.getSanityManager().getTierName(tier);
        String bar = buildSanityBar(sanity);
        String zone = plugin.getZoneManager().getZone(player);
        String zoneName = plugin.getZoneManager().getZoneDisplayName(zone);

        Component msg = Component.text("Tỉnh táo ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(bar).color(getTierColor(tier)))
                .append(Component.text(" " + String.format("%.0f", sanity) + "%")
                        .color(NamedTextColor.WHITE))
                .append(Component.text("  |  " + tierName)
                        .color(getTierColor(tier))
                        .decorate(TextDecoration.ITALIC))
                .append(Component.text("  " + zoneName)
                        .color(NamedTextColor.GRAY));

        player.sendActionBar(msg);
    }

    private void updateBossBar(Player player, double sanity) {
        BossBar bar = bossBars.computeIfAbsent(player.getUniqueId(), uuid -> {
            BossBar newBar = BossBar.bossBar(
                    Component.text("Tỉnh táo").color(NamedTextColor.WHITE),
                    1.0f,
                    BossBar.Color.GREEN,
                    BossBar.Overlay.NOTCHED_10
            );
            player.showBossBar(newBar);
            return newBar;
        });

        float progress = (float) Math.max(0, Math.min(1, sanity / 100.0));
        bar.progress(progress);

        int tier = plugin.getSanityManager().getTierFromValue(sanity);
        BossBar.Color color = switch (tier) {
            case 0 -> BossBar.Color.GREEN;
            case 1 -> BossBar.Color.YELLOW;
            case 2 -> BossBar.Color.RED;
            case 3 -> BossBar.Color.PURPLE;
            default -> BossBar.Color.WHITE;
        };
        bar.color(color);

        String tierName = plugin.getSanityManager().getTierName(tier);
        bar.name(Component.text("⬛ Tỉnh táo — " + tierName)
                .color(getTierColor(tier)));
    }

    private void flashTitle(Player player) {
        String[] messages = {
            "Có gì đó đứng sau mày",
            "Đừng quay lại",
            "Mày không còn một mình",
            "Nó đang nhìn mày",
            "Tần số 3:17"
        };
        String msg = messages[(int)(Math.random() * messages.length)];
        Title title = Title.title(
                Component.text(""),
                Component.text(msg)
                        .color(NamedTextColor.DARK_RED)
                        .decorate(TextDecoration.BOLD),
                Title.Times.times(
                        Duration.ofMillis(100),
                        Duration.ofMillis(800),
                        Duration.ofMillis(300)
                )
        );
        player.showTitle(title);
    }

    private String buildSanityBar(double sanity) {
        int filled = (int) Math.round(sanity / 10.0);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 10; i++) sb.append(i < filled ? "█" : "░");
        sb.append("]");
        return sb.toString();
    }

    private NamedTextColor getTierColor(int tier) {
        return switch (tier) {
            case 0 -> NamedTextColor.GREEN;
            case 1 -> NamedTextColor.YELLOW;
            case 2 -> NamedTextColor.RED;
            case 3 -> NamedTextColor.DARK_PURPLE;
            default -> NamedTextColor.WHITE;
        };
    }
  }
