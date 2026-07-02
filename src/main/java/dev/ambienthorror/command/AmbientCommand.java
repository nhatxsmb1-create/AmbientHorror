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
            case "sanity" -> {
                if (args.length < 3) {
                    if (sender instanceof Player p) {
                        double san = plugin.getSanityManager().getSanity(p);
                        int tier = plugin.getSanityManager().getTierFromValue(san);
                        sender.sendMessage("§b[AmbientHorror] Sanity: §f" +
                                String.format("%.1f", san) + " §7(" +
                                plugin.getSanityManager().getTierName(tier) + ")");
                    } else {
                        sender.sendMessage("§cUsage: /ah sanity <player> <value>");
                    }
                    yield true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) { sender.sendMessage("§cKhông tìm thấy: " + args[1]); yield true; }
                try {
                    double value = Double.parseDouble(args[2]);
                    plugin.getSanityManager().setSanity(target, value);
                    sender.sendMessage("§aSet sanity §f" + target.getName() + " → " + value);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cGiá trị không hợp lệ.");
                }
                yield true;
            }
            case "presence" -> {
                if (args.length < 3) {
                    if (sender instanceof Player p) {
                        double san = plugin.getSanityManager().getSanity(p);
                        int tier = plugin.getSanityManager().getTierFromValue(san);
                        sender.sendMessage("§b[AmbientHorror] Sanity: §f" +
                                String.format("%.1f", san) + " §7(" +
                                plugin.getSanityManager().getTierName(tier) + ")");
                    } else {
                        sender.sendMessage("§cUsage: /ah presence <player> <value>");
                    }
                    yield true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) { sender.sendMessage("§cKhông tìm thấy: " + args[1]); yield true; }
                try {
                    double value = Double.parseDouble(args[2]);
                    plugin.getSanityManager().setSanity(target, value);
                    sender.sendMessage("§aSet sanity §f" + target.getName() + " → " + value);
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
                double sanity = plugin.getSanityManager().getSanity(target);
                String zone = plugin.getZoneManager().getZone(target);
                sender.sendMessage("§b" + target.getName() + ":");
                sender.sendMessage("§7  Director Score: §f" + String.format("%.1f", score));
                sender.sendMessage("§7  Sanity: §f" + String.format("%.1f", sanity) +
                        " §7(" + plugin.getSanityManager().getTierName(
                        plugin.getSanityManager().getTierFromValue(sanity)) + ")");
                sender.sendMessage("§7  Zone: §f" + plugin.getZoneManager().getZoneDisplayName(zone));
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
            case "zone" -> {
                Player target = args.length >= 2
                        ? plugin.getServer().getPlayer(args[1])
                        : (sender instanceof Player p ? p : null);
                if (target == null) { sender.sendMessage("§cUsage: /ah zone <player>"); yield true; }
                String zone = plugin.getZoneManager().getZone(target);
                sender.sendMessage("§b" + target.getName() + " đang ở: §f" +
                        plugin.getZoneManager().getZoneDisplayName(zone));
                yield true;
            }
            default -> { sendHelp(sender); yield true; }
        };
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§b[AmbientHorror] Commands:");
        sender.sendMessage("§7  /ah reload | status | debug");
        sender.sendMessage("§7  /ah sanity <player> <value> §f- Set sanity (0-100)");
        sender.sendMessage("§7  /ah score <player> §f- Xem director score + sanity + zone");
        sender.sendMessage("§7  /ah zone <player> §f- Xem zone hiện tại");
        sender.sendMessage("§7  /ah event <player> <key> §f- Force trigger event");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("ambienthorror.admin")) return List.of();
        if (args.length == 1)
            return Arrays.asList("reload", "status", "debug", "sanity", "presence", "score", "zone", "event");
        if (args.length == 2 && List.of("sanity","presence","score","zone","event").contains(args[0].toLowerCase()))
            return plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("event")) {
            var sec = plugin.getConfigManager().getEventsConfig().getConfigurationSection("events");
            if (sec != null) return sec.getKeys(false).stream().toList();
        }
        return List.of();
    }
                    }
