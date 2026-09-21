package me.pinkysha.pineapple.velocity;

import com.google.inject.Inject;
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
import me.pinkysha.pineapple.velocity.config.VelocityConfig;
import me.pinkysha.pineapple.velocity.server.SessionManager;
import me.pinkysha.pineapple.velocity.server.VelocityWebServer;
import me.pinkysha.pineapple.velocity.telemetry.ServerTelemetryRegistry;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

@Plugin(
        id = "pineapple",
        name = "PineApple",
        version = "1.0.0",
        description = "Network Web Monitor & Telemetry Proxy for Velocity",
        authors = {"Pinkysha"}
)
public class PineAppleVelocity {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private VelocityConfig config;
    private ServerTelemetryRegistry telemetryRegistry;
    private SessionManager sessionManager;
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
        this.webServer = new VelocityWebServer(config, telemetryRegistry, sessionManager, dataDirectory, logger);
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

        // Consume and handle message from backend servers
        String originServer = null;
        if (event.getSource() instanceof ServerConnection conn) {
            originServer = conn.getServerInfo().getName();
        }

        byte[] data = event.getData();
        if (data != null && data.length > 0) {
            String payload = new String(data, StandardCharsets.UTF_8);
            boolean statusChanged = telemetryRegistry.handleIncomingTelemetry(originServer, payload);
            if (statusChanged) {
                logger.info("Received telemetry stream from backend server '{}'.", originServer != null ? originServer : "vanilla");
            }
        }

        event.setResult(PluginMessageEvent.ForwardResult.handled());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
        if (sessionCleanupTask != null) {
            sessionCleanupTask.cancel();
            sessionCleanupTask = null;
        }
        if (channelIdentifier != null) {
            server.getChannelRegistrar().unregister(channelIdentifier);
        }
        if (webServer != null) {
            webServer.stop();
            webServer = null;
        }
        logger.info("PineApple Velocity Telemetry stopped.");
    }

    public ServerTelemetryRegistry getTelemetryRegistry() {
        return telemetryRegistry;
    }

    public VelocityConfig getConfig() {
        return config;
    }
}
