package me.pinkysha.pineapple.paper;

import me.pinkysha.pineapple.paper.command.PineAppleCommand;
import me.pinkysha.pineapple.paper.config.ConfigManager;
import me.pinkysha.pineapple.paper.metrics.StatsCollector;
import me.pinkysha.pineapple.paper.server.SessionManager;
import me.pinkysha.pineapple.paper.server.WebServer;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class PineApplePaper extends JavaPlugin implements Listener {

    private ConfigManager configManager;
    private SessionManager sessionManager;
    private StatsCollector statsCollector;
    private WebServer webServer;
    private HttpClient httpClient;

    private BukkitTask metricsTask;
    private BukkitTask telemetrySendTask;
    private BukkitTask historySaveTask;

    private String currentHost;
    private int currentPort;
    private String registeredChannel;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        File dataDir = getDataFolder();
        if (!dataDir.exists()) {
            boolean ok = dataDir.mkdirs();
            if (!ok && !dataDir.exists()) {
                getLogger().warning("Failed to create plugin data directory: " + dataDir.getAbsolutePath());
            }
        }

        this.configManager = new ConfigManager(this);
        this.sessionManager = new SessionManager(getDataFolder().toPath());
        this.statsCollector = new StatsCollector(configManager);
        this.statsCollector.setCacheTtlMs(configManager.getCacheTtlMs());

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(configManager.getProxyPushTimeoutMs()))
                .build();

        // Register outgoing plugin messaging channel
        this.registeredChannel = configManager.channel();
        getServer().getMessenger().registerOutgoingPluginChannel(this, registeredChannel);

        // Register event listener for immediate telemetry on player joins
        getServer().getPluginManager().registerEvents(this, this);

        // 1. Initial snapshot on main thread
        this.statsCollector.refreshBukkitSnapshot();
        this.statsCollector.recordHistoryPoint();

        // 2. Periodic Bukkit snapshot refresh every 20 ticks (1s) on main thread
        this.metricsTask = getServer().getScheduler().runTaskTimer(this, statsCollector::refreshBukkitSnapshot, 20L, 20L);

        // 3. Periodic telemetry sample & send every 50 ticks (2.5s) on main thread
        this.telemetrySendTask = getServer().getScheduler().runTaskTimer(this, this::sampleAndSendTelemetry, 50L, 50L);

        // Standalone Web Server mode if enabled
        if (configManager.isStandaloneWebServer()) {
            File historyFile = new File(dataDir, "history.json");
            this.statsCollector.loadHistoryFromFile(historyFile);
            this.historySaveTask = getServer().getScheduler().runTaskTimerAsynchronously(this, this::saveHistorySync, 1200L, 1200L);
            startWebServer();
            if (configManager.autoOpenBrowser()) {
                getServer().getScheduler().runTaskLaterAsynchronously(this, this::tryOpenBrowser, 40L);
            }
        }

        registerCommands();

        getLogger().info("PineApple Paper Telemetry enabled for server '" + configManager.serverName() + "' on channel '" + registeredChannel + "'.");
    }

    /**
     * Samples metrics and dispatches telemetry via HTTP push and plugin messaging channel.
     */
    public void sampleAndSendTelemetry() {
        try {
            statsCollector.recordHistoryPoint();
            String payloadJson = statsCollector.buildStatsJson();

            // 1. Asynchronous HTTP push to proxy (reliable even with 0 online players)
            sendTelemetryHttp(payloadJson);

            // 2. Secondary dispatch across plugin messaging channel if players are online
            byte[] bytes = payloadJson.getBytes(StandardCharsets.UTF_8);
            if (!getServer().getOnlinePlayers().isEmpty()) {
                Player player = getServer().getOnlinePlayers().iterator().next();
                player.sendPluginMessage(this, registeredChannel, bytes);
            } else {
                try {
                    getServer().sendPluginMessage(this, registeredChannel, bytes);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            getLogger().fine("Error during telemetry dispatch: " + t.getMessage());
        }
    }

    private void sendTelemetryHttp(String payloadJson) {
        if (!configManager.isProxyHttpPushEnabled() || httpClient == null) {
            return;
        }

        String pushUrl = configManager.getProxyPushUrl();
        if (pushUrl == null || pushUrl.isBlank()) {
            return;
        }

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(pushUrl))
                    .timeout(Duration.ofMillis(configManager.getProxyPushTimeoutMs()))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson, StandardCharsets.UTF_8));

            String token = configManager.getProxySecretToken();
            if (token != null && !token.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + token);
            }

            httpClient.sendAsync(reqBuilder.build(), HttpResponse.BodyHandlers.discarding())
                    .exceptionally(ex -> {
                        getLogger().fine("HTTP telemetry push failed: " + ex.getMessage());
                        return null;
                    });
        } catch (Throwable t) {
            getLogger().fine("Failed to initiate HTTP telemetry push: " + t.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Send instantaneous telemetry refresh upon player connection
        getServer().getScheduler().runTaskLater(this, () -> {
            statsCollector.refreshBukkitSnapshot();
            sampleAndSendTelemetry();
        }, 5L);
    }

    private synchronized void startWebServer() {
        if (webServer != null) {
            webServer.stop();
        }

        this.currentHost = configManager.host();
        this.currentPort = configManager.port();

        try {
            webServer = new WebServer(configManager, currentHost, currentPort, statsCollector, sessionManager, getDataFolder().toPath());
            webServer.start();
        } catch (Exception e) {
            getLogger().severe("Failed to bind Standalone Web Monitor on " + currentHost + ":" + currentPort + ": " + e.getMessage());
        }
    }

    private void registerCommands() {
        PluginCommand cmd = getCommand("pineapple");
        if (cmd != null) {
            PineAppleCommand executor = new PineAppleCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }
    }

    public synchronized void reloadPlugin() {
        configManager.reload();
        statsCollector.setCacheTtlMs(configManager.getCacheTtlMs());
        statsCollector.refreshBukkitSnapshot();
        statsCollector.recordHistoryPoint();

        String newChannel = configManager.channel();
        if (!newChannel.equals(registeredChannel)) {
            getServer().getMessenger().unregisterOutgoingPluginChannel(this, registeredChannel);
            this.registeredChannel = newChannel;
            getServer().getMessenger().registerOutgoingPluginChannel(this, registeredChannel);
        }

        if (configManager.isStandaloneWebServer()) {
            saveHistorySync();
            String newHost = configManager.host();
            int newPort = configManager.port();
            if (webServer == null || !newHost.equals(currentHost) || newPort != currentPort) {
                startWebServer();
            }
        } else if (webServer != null) {
            webServer.stop();
            webServer = null;
        }

        if (configManager.isProxyHttpPushEnabled()) {
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(configManager.getProxyPushTimeoutMs()))
                    .build();
        }

        sampleAndSendTelemetry();
    }

    public String webUrl() {
        String displayHost = currentHost != null && currentHost.equals("0.0.0.0") ? "127.0.0.1" : (currentHost != null ? currentHost : "127.0.0.1");
        return "http://" + displayHost + ":" + currentPort;
    }

    private void tryOpenBrowser() {
        String url = "http://127.0.0.1:" + currentPort;
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
            }
        } catch (Throwable ignored) {}
    }

    private void saveHistorySync() {
        if (statsCollector != null) {
            statsCollector.saveHistoryToFile(new File(getDataFolder(), "history.json"));
        }
    }

    public ConfigManager configManager() {
        return configManager;
    }

    public StatsCollector statsCollector() {
        return statsCollector;
    }

    @Override
    public void onDisable() {
        if (metricsTask != null) {
            metricsTask.cancel();
            metricsTask = null;
        }
        if (telemetrySendTask != null) {
            telemetrySendTask.cancel();
            telemetrySendTask = null;
        }
        if (historySaveTask != null) {
            historySaveTask.cancel();
            historySaveTask = null;
        }

        if (registeredChannel != null) {
            try {
                getServer().getMessenger().unregisterOutgoingPluginChannel(this, registeredChannel);
            } catch (Throwable ignored) {}
        }

        if (configManager != null && configManager.isStandaloneWebServer()) {
            saveHistorySync();
        }
        if (webServer != null) {
            webServer.stop();
            webServer = null;
        }
        getLogger().info("PineApple Paper Telemetry disabled.");
    }
}
