package dev.ambienthorror.command;

import dev.ambienthorror.AmbientHorror;
import dev.ambienthorror.theman.TheMan.Phase;
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
            case "score" -> {
                Player target = args.length >= 2
                        ? plugin.getServer().getPlayer(args[1])
                        : (sender instanceof Player p ? p : null);
                if (target == null) { sender.sendMessage("§cUsage: /ah score <player>"); yield true; }
                double score = plugin.getHorrorDirector().calculateScore(target);
                double sanity = plugin.getSanityManager().getSanity(target);
                String zone = plugin.getZoneManager().getZone(target);
                sender.sendMessage("§b" + target.getName() + ":");
                sender.sendMessage("§7  Score: §f" + String.format("%.1f", score));
                sender.sendMessage("§7  Sanity: §f" + String.format("%.1f", sanity) +
                        " §7(" + plugin.getSanityManager().getTierName(
                        plugin.getSanityManager().getTierFromValue(sanity)) + ")");
                sender.sendMessage("§7  Zone: §f" + plugin.getZoneManager().getZoneDisplayName(zone));
                sender.sendMessage("§7  TheMan active: §f" +
                        plugin.getTheManManager().isActive(target));
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
            case "event" -> {
                if (args.length < 3) { sender.sendMessage("§cUsage: /ah event <player> <key>"); yield true; }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) { sender.sendMessage("§cKhông tìm thấy: " + args[1]); yield true; }
                plugin.getAmbientAPI().playEvent(target, args[2].toUpperCase());
                sender.sendMessage("§aTriggered §f" + args[2] + " → " + target.getName());
                yield true;
            }
            case "theman" -> {
                // /ah theman <player> [phase]
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /ah theman <player> [1|2|3|despawn]");
                    yield true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) { sender.sendMessage("§cKhông tìm thấy: " + args[1]); yield true; }

                String sub = args.length >= 3 ? args[2].toLowerCase() : "1";

                if (sub.equals("despawn")) {
                    plugin.getTheManManager().despawnForPlayer(target);
                    sender.sendMessage("§a[TheMan] Despawned → " + target.getName());
                    yield true;
                }

                Phase phase = switch (sub) {
                    case "2" -> Phase.PHASE_2;
                    case "3" -> Phase.PHASE_3;
                    default  -> Phase.PHASE_1;
                };

                plugin.getTheManManager().forceSpawn(target, phase);
                sender.sendMessage("§a[TheMan] Force spawn Phase " + sub +
                        " → §f" + target.getName());
                yield true;
            }
            default -> { sendHelp(sender); yield true; }
        };
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§b[AmbientHorror] Commands:");
        sender.sendMessage("§7  /ah reload | status | debug");
        sender.sendMessage("§7  /ah sanity <player> <value>");
        sender.sendMessage("§7  /ah score <player>");
        sender.sendMessage("§7  /ah zone <player>");
        sender.sendMessage("§7  /ah event <player> <key>");
        sender.sendMessage("§7  /ah theman <player> [1|2|3|despawn]");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (!sender.hasPermission("ambienthorror.admin")) return List.of();
        if (args.length == 1)
            return Arrays.asList("reload", "status", "debug", "sanity",
                    "score", "zone", "event", "theman");
        if (args.length == 2 && List.of("sanity","score","zone","event","theman")
                .contains(args[0].toLowerCase()))
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName).toList();
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("event")) {
                var sec = plugin.getConfigManager().getEventsConfig()
                        .getConfigurationSection("events");
                if (sec != null) return sec.getKeys(false).stream().toList();
            }
            if (args[0].equalsIgnoreCase("theman"))
                return Arrays.asList("1", "2", "3", "despawn");
        }
        return List.of();
    }
                    }
