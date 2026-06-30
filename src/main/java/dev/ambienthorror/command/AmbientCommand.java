package dev.ambienthorror.command;

import dev.ambienthorror.AmbientHorror;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class AmbientCommand implements CommandExecutor, TabCompleter {

    private final AmbientHorror plugin;

    public AmbientCommand(AmbientHorror plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ambienthorror.admin")) {
            sender.sendMessage("§cKhông có quyền.");
            return true;
        }
        if (args.length == 0) { sendHelp(sender); return true; }

        return switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.getConfigManager().reloadAll();
                sender.sendMessage("§a[AmbientHorror] Reload hoàn tất.");
                yield true;
            }
            case "status" -> {
                sender.sendMessage("§b[AmbientHorror] Status:");
                sender.sendMessage("§7  Enabled: §f" + plugin.getConfigManager().isEnabled());
                sender.sendMessage("§7  Debug: §f" + plugin.getConfigManager().isDebug());
                sender.sendMessage("§7  Players online: §f" + plugin.getServer().getOnlinePlayers().size());
                yield true;
            }
            case "debug" -> {
                boolean current = plugin.getConfigManager().isDebug();
                plugin.getConfigManager().getMainConfig().set("debug", !current);
                sender.sendMessage("§e[AmbientHorror] Debug: §f" + !current);
                yield true;
            }
            case "presence" -> {
                if (args.length < 3) {
                    if (sender instanceof Player p) {
                        double pres = plugin.getPresenceManager().getPresence(p);
                        int tier = plugin.getPresenceManager().getPresenceTier(p);
                        sender.sendMessage("§b[AmbientHorror] Presence: §f" +
                                String.format("%.1f", pres) + " §7(Tier " + tier + ")");
                    } else {
                        sender.sendMessage("§cUsage: /ah presence <player> <value>");
                    }
                    yield true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) { sender.sendMessage("§cKhông tìm thấy: " + args[1]); yield true; }
                try {
                    double value = Double.parseDouble(args[2]);
                    plugin.getPresenceManager().setPresence(target, value);
                    sender.sendMessage("§aSet presence §f" + target.getName() + " → " + value);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cGiá trị không hợp lệ.");
                }
                yield true;
            }
            case "score" -> {
                Player target = args.length >= 2
                        ? plugin.getServer().getPlayer(args[1])
                        : (sender instanceof Player p ? p : null);
                if (target == null) { sender.sendMessage("§cUsage: /ah score <player>"); yield true; }
                double score = plugin.getHorrorDirector().calculateScore(target);
                double presence = plugin.getPresenceManager().getPresence(target);
                sender.sendMessage("§b" + target.getName() + " §7Score:§f " +
                        String.format("%.1f", score) + " §7Presence:§f " +
                        String.format("%.1f", presence));
                yield true;
            }
            case "event" -> {
                if (args.length < 3) { sender.sendMessage("§cUsage: /ah event <player> <key>"); yield true; }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) { sender.sendMessage("§cKhông tìm thấy: " + args[1]); yield true; }
                plugin.getAmbientAPI().playEvent(target, args[2].toUpperCase());
                sender.sendMessage("§aTriggered §f" + args[2] + " → " + target.getName());
                yield true;
            }
            default -> { sendHelp(sender); yield true; }
        };
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§b[AmbientHorror] Commands:");
        sender.sendMessage("§7  /ah reload | status | debug");
        sender.sendMessage("§7  /ah presence [player] [value]");
        sender.sendMessage("§7  /ah score [player]");
        sender.sendMessage("§7  /ah event <player> <key>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("ambienthorror.admin")) return List.of();
        if (args.length == 1)
            return Arrays.asList("reload", "status", "debug", "presence", "score", "event");
        if (args.length == 2 && List.of("presence","event","score").contains(args[0].toLowerCase()))
            return plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("event")) {
            var sec = plugin.getConfigManager().getEventsConfig().getConfigurationSection("events");
            if (sec != null) return sec.getKeys(false).stream().toList();
        }
        return List.of();
    }
}
