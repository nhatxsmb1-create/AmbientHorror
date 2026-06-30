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
                sender
