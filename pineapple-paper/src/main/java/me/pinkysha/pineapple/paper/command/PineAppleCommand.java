package me.pinkysha.pineapple.paper.command;

import me.pinkysha.pineapple.paper.PineApplePaper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class PineAppleCommand implements CommandExecutor, TabCompleter {

    private final PineApplePaper plugin;

    public PineAppleCommand(PineApplePaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("pineapple.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadPlugin();
            sender.sendMessage(Component.text("PineApple configuration reloaded successfully.", NamedTextColor.GREEN));
            return true;
        }

        sender.sendMessage(Component.text("PineApple Telemetry Sampler (Paper 1.21.4)", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Server: " + plugin.configManager().serverName(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Channel: " + plugin.configManager().channel(), NamedTextColor.YELLOW));
        if (plugin.configManager().isStandaloneWebServer()) {
            sender.sendMessage(Component.text("Standalone Web Monitor URL: " + plugin.webUrl(), NamedTextColor.AQUA));
        } else {
            sender.sendMessage(Component.text("Telemetry Mode: Sending to Velocity Proxy", NamedTextColor.GRAY));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("reload", "status");
        }
        return Collections.emptyList();
    }
}
