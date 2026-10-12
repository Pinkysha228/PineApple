package me.pinkysha.pineapple.paper.command;

import me.pinkysha.pineapple.paper.PineApplePaper;
import me.pinkysha.pineapple.paper.permission.PermissionManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
            sender.sendMessage(Component.text("PineApple configuration and permissions reloaded successfully.", NamedTextColor.GREEN));
            return true;
        }

        if (args.length > 0 && (args[0].equalsIgnoreCase("perm") || args[0].equalsIgnoreCase("user"))) {
            handlePermCommand(sender, args);
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
        sender.sendMessage(Component.text("Commands:", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /pineapple reload - Reload configuration and permissions", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /pineapple perm list - List configured permissions on this server", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /pineapple perm set <user> <telemetry> <console_view> <console_command> - Set permissions", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /pineapple perm remove <user> - Remove user from permissions.json", NamedTextColor.WHITE));
        return true;
    }

    private void handlePermCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        PermissionManager pm = plugin.getPermissionManager();
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            Map<String, PermissionManager.UserPerm> perms = pm.getPermissions();
            sender.sendMessage(Component.text("--- PineApple Permissions on " + plugin.configManager().serverName() + " ---", NamedTextColor.GOLD));
            if (perms.isEmpty()) {
                sender.sendMessage(Component.text("No specific permissions configured.", NamedTextColor.GRAY));
                return;
            }
            for (Map.Entry<String, PermissionManager.UserPerm> entry : perms.entrySet()) {
                PermissionManager.UserPerm p = entry.getValue();
                String summary = String.format("telemetry=%s, console_view=%s, console_command=%s",
                        p.telemetry(), p.consoleView(), p.consoleCommand());
                sender.sendMessage(Component.text("• " + entry.getKey() + ": ", NamedTextColor.YELLOW)
                        .append(Component.text(summary, NamedTextColor.WHITE)));
            }
            return;
        }

        String sub = args[1].toLowerCase();
        if (sub.equals("set")) {
            if (args.length < 3) {
                sender.sendMessage(Component.text("Usage: /pineapple perm set <username> <telemetry> <console_view> <console_command>", NamedTextColor.RED));
                sender.sendMessage(Component.text("Example: /pineapple perm set moderator true true false", NamedTextColor.GRAY));
                return;
            }
            String username = args[2].trim().toLowerCase();
            boolean telemetry = args.length > 3 ? Boolean.parseBoolean(args[3]) : true;
            boolean consoleView = args.length > 4 ? Boolean.parseBoolean(args[4]) : false;
            boolean consoleCommand = args.length > 5 ? Boolean.parseBoolean(args[5]) : false;

            pm.setPermission(username, telemetry, consoleView, consoleCommand);
            plugin.sampleAndSendTelemetry();

            sender.sendMessage(Component.text("Permissions updated for user '" + username + "': ", NamedTextColor.GREEN)
                    .append(Component.text("telemetry=" + telemetry + ", console_view=" + consoleView + ", console_command=" + consoleCommand, NamedTextColor.AQUA)));
            sender.sendMessage(Component.text("Pushed updated permissions matrix to proxy.", NamedTextColor.GRAY));
            return;
        }

        if (sub.equals("remove") || sub.equals("delete")) {
            if (args.length < 3) {
                sender.sendMessage(Component.text("Usage: /pineapple perm remove <username>", NamedTextColor.RED));
                return;
            }
            String username = args[2].trim().toLowerCase();
            boolean removed = pm.removePermission(username);
            if (removed) {
                plugin.sampleAndSendTelemetry();
                sender.sendMessage(Component.text("Removed permissions for user '" + username + "'. Pushed to proxy.", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("User '" + username + "' was not found in permissions.json.", NamedTextColor.RED));
            }
            return;
        }

        sender.sendMessage(Component.text("Unknown perm action. Use: list, set, remove.", NamedTextColor.RED));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filterPrefix(List.of("reload", "status", "perm", "user"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("perm") || args[0].equalsIgnoreCase("user"))) {
            return filterPrefix(List.of("list", "set", "remove"), args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("perm") || args[0].equalsIgnoreCase("user"))) {
            if (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("set")) {
                return filterPrefix(new ArrayList<>(plugin.getPermissionManager().getPermissions().keySet()), args[2]);
            }
        }
        if (args.length >= 4 && args.length <= 6 && (args[0].equalsIgnoreCase("perm") || args[0].equalsIgnoreCase("user")) && args[1].equalsIgnoreCase("set")) {
            return filterPrefix(List.of("true", "false"), args[args.length - 1]);
        }
        return Collections.emptyList();
    }

    private List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> matched = new ArrayList<>();
        for (String opt : options) {
            if (opt.toLowerCase().startsWith(lower)) {
                matched.add(opt);
            }
        }
        return matched;
    }
}
