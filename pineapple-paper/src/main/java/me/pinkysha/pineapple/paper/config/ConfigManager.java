package me.pinkysha.pineapple.paper.config;

import me.pinkysha.pineapple.paper.PineApplePaper;
import org.bukkit.configuration.file.FileConfiguration;

public final class ConfigManager implements MonitorConfig {
    private final PineApplePaper plugin;
    private FileConfiguration config;

    public ConfigManager(PineApplePaper plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public FileConfiguration get() {
        return config;
    }

    @Override
    public String serverName() {
        return config.getString("server-name", "vanilla");
    }

    @Override
    public String channel() {
        return config.getString("channel", "pineapple:telemetry");
    }

    @Override
    public boolean isStandaloneWebServer() {
        return config.getBoolean("standalone-webserver", false);
    }

    public boolean isProxyHttpPushEnabled() {
        return config.getBoolean("proxy.enable_http_push", true);
    }

    public String getProxyPushUrl() {
        return config.getString("proxy.url", "http://127.0.0.1:8080/api/telemetry/push");
    }

    public String getProxySecretToken() {
        return config.getString("proxy.secret_token", "");
    }

    public int getProxyPushTimeoutMs() {
        return config.getInt("proxy.timeout_ms", 2000);
    }

    public String host() {
        return config.getString("server.host", "0.0.0.0");
    }

    public int port() {
        return config.getInt("server.port", 8080);
    }

    public boolean autoOpenBrowser() {
        return config.getBoolean("server.auto_open_browser", false);
    }

    @Override
    public boolean isAuthEnabled() {
        return config.getBoolean("auth.enabled", true);
    }

    @Override
    public String getUsername() {
        return config.getString("auth.username", "admin");
    }

    @Override
    public String getPassword() {
        return config.getString("auth.password", "admin");
    }

    @Override
    public int getRememberMeDays() {
        return config.getInt("auth.remember_me_days", 30);
    }

    @Override
    public boolean isShowAvatars() {
        return config.getBoolean("monitor.show_avatars", true);
    }

    @Override
    public int getMaxPlayersDisplay() {
        return config.getInt("monitor.max_players_display", 12);
    }

    @Override
    public long getCacheTtlMs() {
        return config.getLong("performance.cache_ttl_ms", 1500L);
    }

    @Override
    public void logInfo(String message) {
        plugin.getLogger().info(message);
    }

    @Override
    public void logWarning(String message) {
        plugin.getLogger().warning(message);
    }

    @Override
    public void logSevere(String message) {
        plugin.getLogger().severe(message);
    }
}
