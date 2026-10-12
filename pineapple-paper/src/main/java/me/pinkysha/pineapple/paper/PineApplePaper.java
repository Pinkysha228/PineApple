package me.pinkysha.pineapple.paper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.pinkysha.pineapple.paper.command.PineAppleCommand;
import me.pinkysha.pineapple.paper.config.ConfigManager;
import me.pinkysha.pineapple.paper.console.ConsoleLogManager;
import me.pinkysha.pineapple.paper.metrics.StatsCollector;
import me.pinkysha.pineapple.paper.permission.PermissionManager;
import me.pinkysha.pineapple.paper.server.SessionManager;
import me.pinkysha.pineapple.paper.server.WebServer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
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
    private PermissionManager permissionManager;
    private ConsoleLogManager consoleLogManager;
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
        this.permissionManager = new PermissionManager(this);
        this.consoleLogManager = new ConsoleLogManager();

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(configManager.getProxyPushTimeoutMs()))
                .build();

        // Register plugin messaging outgoing channel
        this.registeredChannel = configManager.channel();
        getServer().getMessenger().registerOutgoingPluginChannel(this, registeredChannel);

        // Register event listener for immediate telemetry on player joins
        getServer().getPluginManager().registerEvents(this, this);

        // 1. Initial Bukkit snapshot (main thread)
        statsCollector.refreshBukkitSnapshot();

        // 2. Periodic Bukkit snapshot every 20 ticks (1s) on main thread
        this.metricsTask = getServer().getScheduler().runTaskTimer(this, () -> {
            statsCollector.refreshBukkitSnapshot();
        }, 20L, 20L);

        // 3. Periodic telemetry sample & send every 50 ticks (2.5s) on main thread
        this.telemetrySendTask = getServer().getScheduler().runTaskTimer(this, this::sampleAndSendTelemetry, 50L, 50L);

        // 4. Register commands
        var cmd = getCommand("pineapple");
        if (cmd != null) {
            cmd.setExecutor(new PineAppleCommand(this));
        }

        // 5. Standalone web server mode
        if (configManager.isStandaloneWebServer()) {
            startWebServer();
            this.historySaveTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                saveHistorySync();
            }, 6000L, 6000L);
        }

        getLogger().info("PineApple Paper Telemetry enabled for server '" + configManager.serverName() + "' on channel '" + registeredChannel + "'.");
    }

    /**
     * Samples metrics and dispatches telemetry via HTTP push and plugin messaging channel.
     */
    public void sampleAndSendTelemetry() {
        try {
            statsCollector.recordHistoryPoint();
            String payloadJson = statsCollector.buildStatsJson();

            // Enrich payload with permissions and console logs
            JsonObject root = JsonParser.parseString(payloadJson).getAsJsonObject();
            root.add("permissions", permissionManager.getPermissionsJson());

            JsonArray logsArr = new JsonArray();
            for (String line : consoleLogManager.getRecentLogs()) {
                logsArr.add(line);
            }
            root.add("logs", logsArr);

            String fullPayload = root.toString();

            // 1. Asynchronous HTTP push to proxy (reliable even with 0 online players)
            sendTelemetryHttp(fullPayload);

            // 2. Secondary dispatch across plugin messaging channel if players are online
            byte[] bytes = fullPayload.getBytes(StandardCharsets.UTF_8);
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

            httpClient.sendAsync(reqBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .thenAccept(res -> {
                        if (res.statusCode() == 200) {
                            handleProxyResponse(res.body());
                        }
                    })
                    .exceptionally(ex -> {
                        getLogger().fine("HTTP telemetry push failed: " + ex.getMessage());
                        return null;
                    });
        } catch (Throwable t) {
            getLogger().fine("Failed to initiate HTTP telemetry push: " + t.getMessage());
        }
    }

    private void handleProxyResponse(String body) {
        if (body == null || body.isBlank()) return;
        try {
            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
            if (obj.has("pending_commands") && obj.get("pending_commands").isJsonArray()) {
                JsonArray cmds = obj.getAsJsonArray("pending_commands");
                for (JsonElement el : cmds) {
                    String cmd = el.getAsString();
                    if (!cmd.isBlank()) {
                        getServer().getScheduler().runTask(this, () -> {
                            getLogger().info("[PineApple] Executing web command: " + cmd);
                            consoleLogManager.appendManual("> " + cmd);
                            getServer().dispatchCommand(getServer().getConsoleSender(), cmd);
                        });
                    }
                }
            }
        } catch (Exception ignored) {}
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
            webServer = null;
        }

        this.currentHost = configManager.host();
        this.currentPort = configManager.port();

        try {
            this.webServer = new WebServer(configManager, currentHost, currentPort, statsCollector, sessionManager, getDataFolder().toPath());
            this.webServer.start();
        } catch (IOException e) {
            getLogger().severe("Could not start PineApple embedded WebServer: " + e.getMessage());
        }
    }

    public synchronized void reload() {
        reloadConfig();
        configManager.reload();
        permissionManager.reload();

        if (statsCollector != null) {
            statsCollector.setCacheTtlMs(configManager.getCacheTtlMs());
        }

        if (registeredChannel != null && !registeredChannel.equals(configManager.channel())) {
            try {
                getServer().getMessenger().unregisterOutgoingPluginChannel(this, registeredChannel);
            } catch (Throwable ignored) {}
            this.registeredChannel = configManager.channel();
            getServer().getMessenger().registerOutgoingPluginChannel(this, registeredChannel);
        }

        if (httpClient != null) {
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(configManager.getProxyPushTimeoutMs()))
                    .build();
        }

        if (configManager.isStandaloneWebServer()) {
            if (webServer == null || !currentHost.equals(configManager.host()) || currentPort != configManager.port()) {
                startWebServer();
            }
        } else {
            if (webServer != null) {
                webServer.stop();
                webServer = null;
                getLogger().info("PineApple standalone WebServer stopped (proxy mode active).");
            }
        }

        sampleAndSendTelemetry();
        getLogger().info("PineApple Paper configuration & permissions reloaded.");
    }

    public void reloadPlugin() {
        reload();
    }

    public String webUrl() {
        return "http://" + (currentHost != null ? currentHost : configManager.host()) + ":" + (currentPort > 0 ? currentPort : configManager.port());
    }

    public void saveHistorySync() {
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

    public PermissionManager getPermissionManager() {
        return permissionManager;
    }

    public ConsoleLogManager getConsoleLogManager() {
        return consoleLogManager;
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
        if (consoleLogManager != null) {
            consoleLogManager.shutdown();
        }
        getLogger().info("PineApple Paper Telemetry disabled.");
    }
}
