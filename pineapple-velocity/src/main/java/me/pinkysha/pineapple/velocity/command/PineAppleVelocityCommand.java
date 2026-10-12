package me.pinkysha.pineapple.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import me.pinkysha.pineapple.velocity.PineAppleVelocity;
import me.pinkysha.pineapple.velocity.config.VelocityConfig;
import me.pinkysha.pineapple.velocity.telemetry.ServerTelemetryRegistry;
import me.pinkysha.pineapple.velocity.user.UserManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.util.List;

public class PineAppleVelocityCommand implements SimpleCommand {

    private final PineAppleVelocity plugin;
    private final VelocityConfig config;
    private final UserManager userManager;
    private final ServerTelemetryRegistry telemetryRegistry;
    private final Logger logger;

    public PineAppleVelocityCommand(PineAppleVelocity plugin, VelocityConfig config,
                                    UserManager userManager,
                                    ServerTelemetryRegistry telemetryRegistry, Logger logger) {
        this.plugin = plugin;
        this.config = config;
        this.userManager = userManager;
        this.telemetryRegistry = telemetryRegistry;
        this.logger = logger;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            source.sendMessage(Component.text("PineApple Velocity Network Monitor v1.0.0", NamedTextColor.GOLD));
            source.sendMessage(Component.text("Commands:", NamedTextColor.YELLOW));
            source.sendMessage(Component.text("  /pineapple changepassword <newPassword> - Change web panel admin password", NamedTextColor.GRAY));
            source.sendMessage(Component.text("  /pineapple user add <user> <pass> [isSuperAdmin:true|false] - Add user", NamedTextColor.GRAY));
            source.sendMessage(Component.text("  /pineapple user passwd <user> <pass> - Change user password", NamedTextColor.GRAY));
            source.sendMessage(Component.text("  /pineapple user remove <user> - Remove user", NamedTextColor.GRAY));
            source.sendMessage(Component.text("  /pineapple user list - List registered users", NamedTextColor.GRAY));
            source.sendMessage(Component.text("  /pineapple reload - Reload configuration", NamedTextColor.GRAY));
            source.sendMessage(Component.text("  /pineapple status - Display proxy monitor status", NamedTextColor.GRAY));
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "changepassword", "password" -> {
                if (args.length < 2) {
                    source.sendMessage(Component.text("Usage: /pineapple changepassword <newPassword>", NamedTextColor.RED));
                    return;
                }
                String newPass = args[1].trim();
                if (newPass.length() < 4) {
                    source.sendMessage(Component.text("Password must be at least 4 characters long.", NamedTextColor.RED));
                    return;
                }
                boolean updated = userManager.updatePassword("admin", newPass);
                if (updated) {
                    source.sendMessage(Component.text("PineApple: Admin password updated successfully with new salt!", NamedTextColor.GREEN));
                    logger.info("Admin password was successfully updated via command.");
                } else {
                    source.sendMessage(Component.text("PineApple: Failed to update password. Check server logs.", NamedTextColor.RED));
                }
            }
            case "user" -> {
                if (args.length < 2) {
                    source.sendMessage(Component.text("Usage: /pineapple user <add|passwd|remove|list> ...", NamedTextColor.RED));
                    return;
                }
                String userAction = args[1].toLowerCase();
                switch (userAction) {
                    case "add" -> {
                        if (args.length < 4) {
                            source.sendMessage(Component.text("Usage: /pineapple user add <username> <password> [isSuperAdmin:true|false]", NamedTextColor.RED));
                            return;
                        }
                        String user = args[2].trim();
                        String pass = args[3].trim();
                        boolean isSuper = args.length >= 5 && "true".equalsIgnoreCase(args[4]);
                        if (pass.length() < 4) {
                            source.sendMessage(Component.text("Password must be at least 4 characters long.", NamedTextColor.RED));
                            return;
                        }
                        if (userManager.addUser(user, pass, isSuper)) {
                            source.sendMessage(Component.text("User '" + user + "' successfully created! Superadmin: " + isSuper, NamedTextColor.GREEN));
                        } else {
                            source.sendMessage(Component.text("Failed to create user. User may already exist.", NamedTextColor.RED));
                        }
                    }
                    case "passwd" -> {
                        if (args.length < 4) {
                            source.sendMessage(Component.text("Usage: /pineapple user passwd <username> <newPassword>", NamedTextColor.RED));
                            return;
                        }
                        String user = args[2].trim();
                        String pass = args[3].trim();
                        if (pass.length() < 4) {
                            source.sendMessage(Component.text("Password must be at least 4 characters long.", NamedTextColor.RED));
                            return;
                        }
                        if (userManager.updatePassword(user, pass)) {
                            source.sendMessage(Component.text("Password for user '" + user + "' updated successfully!", NamedTextColor.GREEN));
                        } else {
                            source.sendMessage(Component.text("Failed to update password. Check if user exists.", NamedTextColor.RED));
                        }
                    }
                    case "remove" -> {
                        if (args.length < 3) {
                            source.sendMessage(Component.text("Usage: /pineapple user remove <username>", NamedTextColor.RED));
                            return;
                        }
                        String user = args[2].trim();
                        if (userManager.removeUser(user)) {
                            source.sendMessage(Component.text("User '" + user + "' removed.", NamedTextColor.GREEN));
                        } else {
                            source.sendMessage(Component.text("Could not remove user '" + user + "'. (Cannot remove primary admin)", NamedTextColor.RED));
                        }
                    }
                    case "list" -> {
                        source.sendMessage(Component.text("Registered Web Users:", NamedTextColor.GOLD));
                        userManager.getAllUsers().forEach((u, entry) -> {
                            source.sendMessage(Component.text(" - " + u + (entry.isSuperAdmin() ? " [SuperAdmin]" : " [User]"), NamedTextColor.YELLOW));
                        });
                    }
                    default -> source.sendMessage(Component.text("Unknown user subcommand: " + userAction, NamedTextColor.RED));
                }
            }
            case "reload" -> {
                config.reload();
                userManager.load();
                source.sendMessage(Component.text("PineApple: Configuration & users reloaded successfully.", NamedTextColor.GREEN));
                logger.info("PineApple configuration reloaded via command.");
            }
            case "status" -> {
                source.sendMessage(Component.text("PineApple Velocity Telemetry Status", NamedTextColor.GOLD));
                source.sendMessage(Component.text("  Web Monitor Port: " + config.port(), NamedTextColor.YELLOW));
                source.sendMessage(Component.text("  Auth Enabled: " + config.isAuthEnabled(), NamedTextColor.YELLOW));
                source.sendMessage(Component.text("  Total Users: " + userManager.getAllUsers().size(), NamedTextColor.YELLOW));
                source.sendMessage(Component.text("  Registered Backend Servers: " + telemetryRegistry.getAllServers().size(), NamedTextColor.YELLOW));
            }
            default -> {
                source.sendMessage(Component.text("Unknown subcommand: " + args[0], NamedTextColor.RED));
                source.sendMessage(Component.text("Usage: /pineapple [changepassword|user|reload|status]", NamedTextColor.GRAY));
            }
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            return List.of("changepassword", "user", "reload", "status");
        }
        if (args.length == 2 && "user".equalsIgnoreCase(args[0])) {
            return List.of("add", "passwd", "remove", "list");
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("pineapple.admin");
    }
}
