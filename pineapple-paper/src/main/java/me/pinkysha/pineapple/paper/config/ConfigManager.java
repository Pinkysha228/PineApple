package me.pinkysha.pineapple.paper.config;

import me.pinkysha.pineapple.paper.PineApplePaper;
import me.pinkysha.pineapple.paper.security.PasswordSecurity;
import me.pinkysha.pineapple.paper.server.HttpSecurity;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.net.URI;

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
        secureStoredPassword();
        warnAboutInsecureProxyLink();
    }

    /**
     * Never keeps the web password in plaintext: a value in auth.password is hashed into
     * auth.password_hash / auth.password_salt and removed. The old "admin" default is
     * replaced by a random password that is printed to the console once.
     */
    private void secureStoredPassword() {
        String plain = config.getString("auth.password", "");
        String hash = config.getString("auth.password_hash", "");
        String salt = config.getString("auth.password_salt", "");

        String toStore = null;
        if (plain != null && !plain.isBlank()) {
            if ("admin".equals(plain.trim())) {
                toStore = announceGeneratedPassword("auth.password was the insecure default \"admin\"");
            } else {
                toStore = plain.trim();
                if (toStore.length() < PasswordSecurity.MIN_PASSWORD_LENGTH) {
                    plugin.getLogger().warning("[PineApple] Web password is shorter than "
                            + PasswordSecurity.MIN_PASSWORD_LENGTH + " characters; consider a longer one.");
                }
                plugin.getLogger().info("[PineApple] Plaintext auth.password has been hashed and removed from config.yml.");
            }
        } else if (hash == null || hash.isBlank() || salt == null || salt.isBlank()) {
            toStore = announceGeneratedPassword("no web password was configured");
        }

        if (toStore != null) {
            String newSalt = PasswordSecurity.generateSalt();
            config.set("auth.password", null);
            config.set("auth.password_hash", PasswordSecurity.hashPassword(toStore, newSalt));
            config.set("auth.password_salt", newSalt);
            plugin.saveConfig();
        }
        HttpSecurity.restrictToOwner(new File(plugin.getDataFolder(), "config.yml").toPath());
    }

    private String announceGeneratedPassword(String reason) {
        String generated = PasswordSecurity.generateRandomPassword(16);
        plugin.getLogger().warning("[PineApple] " + reason + ". A random web password was generated:");
        plugin.getLogger().warning("[PineApple]   username: " + getUsername() + "   password: " + generated);
        plugin.getLogger().warning("[PineApple] To choose your own, set auth.password in config.yml and run /pineapple reload.");
        return generated;
    }

    /**
     * Pending console commands come back in the push response, so a plaintext link over an
     * untrusted network would let anyone on the path run commands as the server console.
     */
    private void warnAboutInsecureProxyLink() {
        if (!isProxyHttpPushEnabled()) return;
        if (getProxySecretToken().isBlank()) {
            plugin.getLogger().warning("[PineApple] proxy.secret_token is empty: the Velocity proxy will reject telemetry. "
                    + "Copy telemetry.secret_token from the proxy's config.properties.");
        }
        try {
            URI uri = URI.create(getProxyPushUrl());
            if ("http".equalsIgnoreCase(uri.getScheme()) && !isPrivateHost(uri.getHost())) {
                plugin.getLogger().warning("[PineApple] proxy.url uses plain HTTP to a public host (" + uri.getHost() + "). "
                        + "The secret token and console commands travel unencrypted; use https:// or a private network.");
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[PineApple] proxy.url is not a valid URL: " + getProxyPushUrl());
        }
    }

    private static boolean isPrivateHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase();
        if (h.equals("localhost") || h.startsWith("127.") || h.equals("[::1]") || h.equals("::1")
                || h.startsWith("10.") || h.startsWith("192.168.")) {
            return true;
        }
        if (h.startsWith("172.")) {
            String[] parts = h.split("\\.");
            try {
                int second = Integer.parseInt(parts[1]);
                return second >= 16 && second <= 31;
            } catch (RuntimeException ignored) {
                return false;
            }
        }
        return false;
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
    public boolean verifyPassword(String candidate) {
        return PasswordSecurity.verifyPassword(candidate,
                config.getString("auth.password_salt", ""),
                config.getString("auth.password_hash", ""));
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
    public String getDefaultLanguage() {
        return config.getString("default_language", "en").trim().toLowerCase();
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
