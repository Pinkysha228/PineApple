package me.pinkysha.pineapple.velocity;

import me.pinkysha.pineapple.velocity.security.PasswordSecurity;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import me.pinkysha.pineapple.velocity.command.PineAppleVelocityCommand;
import me.pinkysha.pineapple.velocity.config.VelocityConfig;
import me.pinkysha.pineapple.velocity.server.SessionManager;
import me.pinkysha.pineapple.velocity.server.VelocityWebServer;
import me.pinkysha.pineapple.velocity.telemetry.ServerTelemetryRegistry;
import me.pinkysha.pineapple.velocity.user.UserManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

@Plugin(
        id = "pineapple-velocity",
        name = "PineApple Velocity",
        version = "1.0.0",
        description = "Unified multi-server telemetry & web monitoring hub for Velocity proxies",
        authors = {"pinkysha"}
)
public class PineAppleVelocity {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private VelocityConfig config;
    private ServerTelemetryRegistry telemetryRegistry;
    private SessionManager sessionManager;
    private UserManager userManager;
    private VelocityWebServer webServer;
    private MinecraftChannelIdentifier channelIdentifier;

    private ScheduledTask timeoutTask;
    private ScheduledTask sessionCleanupTask;

    @Inject
    public PineAppleVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        this.config = new VelocityConfig(dataDirectory, logger);
        this.telemetryRegistry = new ServerTelemetryRegistry();
        this.sessionManager = new SessionManager(dataDirectory);
        this.userManager = new UserManager(dataDirectory, config, logger);
        this.userManager.setCredentialsRevokedListener(sessionManager::invalidateUser);

        // Register proxy commands
        CommandManager commandManager = server.getCommandManager();
        CommandMeta mainMeta = commandManager.metaBuilder("pineapple")
                .aliases("pap", "pmon")
                .plugin(this)
                .build();
        commandManager.register(mainMeta, new PineAppleVelocityCommand(this, config, userManager, telemetryRegistry, logger));

        // Direct /changepassword convenience command
        CommandMeta changePassMeta = commandManager.metaBuilder("changepassword")
                .plugin(this)
                .build();
        commandManager.register(changePassMeta, (SimpleCommand) invocation -> {
            if (!invocation.source().hasPermission("pineapple.admin")) {
                invocation.source().sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return;
            }
            String[] args = invocation.arguments();
            if (args.length < 1) {
                invocation.source().sendMessage(Component.text("Usage: /changepassword <newPassword>", NamedTextColor.RED));
                return;
            }
            String newPass = args[0].trim();
            if (newPass.length() < PasswordSecurity.MIN_PASSWORD_LENGTH) {
                invocation.source().sendMessage(Component.text("Password must be at least " + PasswordSecurity.MIN_PASSWORD_LENGTH + " characters long.", NamedTextColor.RED));
                return;
            }
            if (userManager.updatePassword("admin", newPass)) {
                invocation.source().sendMessage(Component.text("PineApple: Password updated successfully! A new cryptographic salt was applied.", NamedTextColor.GREEN));
                logger.info("Admin password was successfully updated via /changepassword.");
            } else {
                invocation.source().sendMessage(Component.text("PineApple: Failed to update password. Check server logs.", NamedTextColor.RED));
            }
        });

        // Register channel identifier
        String[] channelParts = config.channel().split(":", 2);
        this.channelIdentifier = channelParts.length == 2
                ? MinecraftChannelIdentifier.create(channelParts[0], channelParts[1])
                : MinecraftChannelIdentifier.create("pineapple", "telemetry");
        server.getChannelRegistrar().register(channelIdentifier);

        // Pre-register all configured proxy servers
        for (RegisteredServer rs : server.getAllServers()) {
            telemetryRegistry.registerServer(rs.getServerInfo().getName());
        }

        // Start WebServer
        this.webServer = new VelocityWebServer(config, telemetryRegistry, sessionManager, userManager, dataDirectory, logger);
        try {
            this.webServer.start();
        } catch (IOException e) {
            logger.error("Failed to start PineApple WebServer: {}", e.getMessage(), e);
        }

        // Periodic timeout checker (>10s timeout)
        this.timeoutTask = server.getScheduler()
                .buildTask(this, () -> telemetryRegistry.checkServerTimeouts(10_000L))
                .repeat(Duration.ofSeconds(1))
                .schedule();

        // Periodic session cleanup
        this.sessionCleanupTask = server.getScheduler()
                .buildTask(this, () -> sessionManager.cleanup())
                .repeat(Duration.ofHours(1))
                .schedule();

        logger.info("PineApple Velocity Telemetry initialized on channel '{}'.", config.channel());
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(channelIdentifier)) {
            return;
        }

        // Only accept telemetry that originates from a backend server connection.
        // Player clients can also send on this channel; their payload carries
        // permissions and must never be trusted, so reject anything else.
        if (!(event.getSource() instanceof ServerConnection conn)) {
            event.setResult(PluginMessageEvent.ForwardResult.handled());
            return;
        }
        String originServer = conn.getServerInfo().getName();

        String rawJson = new String(event.getData(), StandardCharsets.UTF_8);
        boolean isNew = telemetryRegistry.handleIncomingTelemetry(originServer, rawJson);
        if (isNew) {
            logger.info("Backend server '{}' connected to PineApple Telemetry.", originServer != null ? originServer : "unknown");
        }

        // Prevent forwarding this packet to player client
        event.setResult(PluginMessageEvent.ForwardResult.handled());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (timeoutTask != null) {
            timeoutTask.cancel();
        }
        if (sessionCleanupTask != null) {
            sessionCleanupTask.cancel();
        }
        if (webServer != null) {
            webServer.stop();
        }
        logger.info("PineApple Velocity Telemetry shut down.");
    }

    public VelocityConfig getConfig() {
        return config;
    }

    public ServerTelemetryRegistry getTelemetryRegistry() {
        return telemetryRegistry;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public UserManager getUserManager() {
        return userManager;
    }
}
