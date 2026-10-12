package me.pinkysha.pineapple.velocity.config;

import me.pinkysha.pineapple.velocity.security.PasswordSecurity;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class VelocityConfig {

    private final Path configFile;
    private final Logger logger;

    private String host = "0.0.0.0";
    private int port = 8080;
    private String channel = "pineapple:telemetry";
    private String telemetrySecret = "";
    private boolean authEnabled = true;
    private String username = "admin";
    private String passwordHash = "";
    private String passwordSalt = "";
    private int rememberMeDays = 30;
    private String defaultLanguage = "en";

    private boolean firstLaunch = false;
    private String generatedPassword = null;

    public VelocityConfig(Path dataDirectory, Logger logger) {
        this.configFile = dataDirectory.resolve("config.properties");
        this.logger = logger;
        loadOrCreate();
    }

    public synchronized void reload() {
        loadOrCreate();
    }

    public synchronized void loadOrCreate() {
        try {
            if (!Files.exists(configFile.getParent())) {
                Files.createDirectories(configFile.getParent());
            }

            if (Files.exists(configFile)) {
                Properties props = new Properties();
                try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                    props.load(reader);
                }
                this.host = props.getProperty("server.host", "0.0.0.0").trim();
                this.port = parseInt(props.getProperty("server.port", "8080"), 8080);
                this.channel = props.getProperty("telemetry.channel", "pineapple:telemetry").trim();
                this.authEnabled = Boolean.parseBoolean(props.getProperty("auth.enabled", "true").trim());
                this.username = props.getProperty("auth.username", "admin").trim();
                this.rememberMeDays = parseInt(props.getProperty("auth.remember_me_days", "30"), 30);
                this.defaultLanguage = props.getProperty("server.default_language", "en").trim().toLowerCase();
                this.telemetrySecret = props.getProperty("telemetry.secret_token", "").trim();

                String plainPassword = props.getProperty("auth.password");
                String hash = props.getProperty("auth.password_hash");
                String salt = props.getProperty("auth.password_salt");

                boolean needsSave = false;

                // Ensure a telemetry secret exists so the push endpoint and plugin channel
                // can authenticate backend servers. Generated once and persisted.
                if (this.telemetrySecret.isEmpty()) {
                    this.telemetrySecret = generateSecret();
                    props.setProperty("telemetry.secret_token", this.telemetrySecret);
                    needsSave = true;
                    logTelemetrySecretNotice();
                }

                if (plainPassword != null && !plainPassword.isBlank()) {
                    // Plaintext password detected in config:
                    // Securely hash with a freshly generated salt and remove plaintext auth.password
                    String newSalt = PasswordSecurity.generateSalt();
                    String newHash = PasswordSecurity.hashPassword(plainPassword.trim(), newSalt);
                    this.passwordSalt = newSalt;
                    this.passwordHash = newHash;

                    props.setProperty("auth.password_hash", newHash);
                    props.setProperty("auth.password_salt", newSalt);
                    props.remove("auth.password");
                    needsSave = true;
                    logger.info("[PineApple] Plaintext password in config.properties has been securely hashed with a new salt.");
                } else if (hash != null && !hash.isBlank() && salt != null && !salt.isBlank()) {
                    this.passwordHash = hash.trim();
                    this.passwordSalt = salt.trim();
                } else {
                    // Config file exists but has no password configured -> treat as first password setup
                    initFirstLaunchPassword(props);
                    needsSave = true;
                }

                if (needsSave) {
                    saveProperties(props);
                }
            } else {
                saveDefaultFirstLaunch();
            }
        } catch (IOException e) {
            logger.warn("Failed to load or create config.properties: {}", e.getMessage());
        }
    }

    private void saveDefaultFirstLaunch() {
        this.firstLaunch = true;
        this.generatedPassword = PasswordSecurity.generateRandomPassword(16);
        this.passwordSalt = PasswordSecurity.generateSalt();
        this.passwordHash = PasswordSecurity.hashPassword(this.generatedPassword, this.passwordSalt);
        this.telemetrySecret = generateSecret();

        Properties props = new Properties();
        props.setProperty("server.host", host);
        props.setProperty("server.port", String.valueOf(port));
        props.setProperty("telemetry.channel", channel);
        props.setProperty("telemetry.secret_token", telemetrySecret);
        props.setProperty("auth.enabled", String.valueOf(authEnabled));
        props.setProperty("auth.username", username);
        props.setProperty("auth.password_hash", passwordHash);
        props.setProperty("auth.password_salt", passwordSalt);
        props.setProperty("auth.remember_me_days", String.valueOf(rememberMeDays));
        props.setProperty("server.default_language", defaultLanguage);

        saveProperties(props);
        logFirstLaunchNotice();
        logTelemetrySecretNotice();
    }

    private static String generateSecret() {
        // 32 bytes of entropy, hex-encoded (two 16-byte salts concatenated).
        return PasswordSecurity.generateSalt() + PasswordSecurity.generateSalt();
    }

    private void logTelemetrySecretNotice() {
        logger.warn("================================================================================");
        logger.warn("[PineApple] A telemetry secret token has been generated for this proxy.");
        logger.warn("[PineApple] Backend servers MUST send it to be trusted. Set on each Paper server:");
        logger.warn("[PineApple]   proxy.secret_token: {}", telemetrySecret);
        logger.warn("[PineApple] Until then, telemetry from those servers will be rejected.");
        logger.warn("================================================================================");
    }

    private void initFirstLaunchPassword(Properties props) {
        this.firstLaunch = true;
        this.generatedPassword = PasswordSecurity.generateRandomPassword(16);
        this.passwordSalt = PasswordSecurity.generateSalt();
        this.passwordHash = PasswordSecurity.hashPassword(this.generatedPassword, this.passwordSalt);

        props.setProperty("auth.password_hash", passwordHash);
        props.setProperty("auth.password_salt", passwordSalt);
        props.remove("auth.password");

        logFirstLaunchNotice();
    }

    private void logFirstLaunchNotice() {
        logger.warn("================================================================================");
        logger.warn("[PineApple] FIRST LAUNCH DETECTED!");
        logger.warn("[PineApple] A secure random admin password has been initialized.");
        logger.warn("[PineApple] ATTENTION: You MUST change the password to gain access!");
        logger.warn("[PineApple] Use proxy console command: /pineapple changepassword <newPassword>");
        logger.warn("================================================================================");
    }

    /**
     * Updates the password, generating a new cryptographic salt each time.
     *
     * @param newPlaintext New password in plaintext.
     * @return true if updated and saved successfully, false otherwise.
     */
    public synchronized boolean updatePassword(String newPlaintext) {
        if (newPlaintext == null || newPlaintext.isBlank()) {
            return false;
        }

        // Salt changes on EVERY password change!
        String newSalt = PasswordSecurity.generateSalt();
        String newHash = PasswordSecurity.hashPassword(newPlaintext.trim(), newSalt);

        this.passwordSalt = newSalt;
        this.passwordHash = newHash;

        try {
            Properties props = new Properties();
            if (Files.exists(configFile)) {
                try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                    props.load(reader);
                }
            }
            props.setProperty("server.host", host);
            props.setProperty("server.port", String.valueOf(port));
            props.setProperty("telemetry.channel", channel);
            props.setProperty("auth.enabled", String.valueOf(authEnabled));
            props.setProperty("auth.username", username);
            props.setProperty("auth.password_hash", newHash);
            props.setProperty("auth.password_salt", newSalt);
            props.setProperty("auth.remember_me_days", String.valueOf(rememberMeDays));
            props.setProperty("server.default_language", defaultLanguage);
            props.remove("auth.password");

            saveProperties(props);
            logger.info("[PineApple] Admin password updated successfully with a new cryptographic salt.");
            return true;
        } catch (IOException e) {
            logger.error("[PineApple] Failed to persist updated password to config.properties: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verifies a candidate password against the stored salt and hash.
     *
     * @param candidate Candidate password.
     * @return true if password matches or auth is disabled, false otherwise.
     */
    public boolean verifyPassword(String candidate) {
        if (!authEnabled) {
            return true;
        }
        if (candidate == null || passwordHash.isEmpty() || passwordSalt.isEmpty()) {
            return false;
        }
        return PasswordSecurity.verifyPassword(candidate, passwordSalt, passwordHash);
    }

    private void saveProperties(Properties props) {
        StringBuilder sb = new StringBuilder();
        sb.append("# PineApple Velocity Proxy Monitor Configuration\n\n");
        sb.append("# Web Server Settings\n");
        sb.append("server.host=").append(props.getProperty("server.host", host)).append("\n");
        sb.append("server.port=").append(props.getProperty("server.port", String.valueOf(port))).append("\n");
        sb.append("server.default_language=").append(props.getProperty("server.default_language", defaultLanguage)).append("\n\n");

        sb.append("# Telemetry Channel\n");
        sb.append("telemetry.channel=").append(props.getProperty("telemetry.channel", channel)).append("\n");
        sb.append("# Shared secret backend servers must present (Authorization: Bearer / plugin channel).\n");
        sb.append("telemetry.secret_token=").append(props.getProperty("telemetry.secret_token", telemetrySecret)).append("\n\n");

        sb.append("# Authentication Settings\n");
        sb.append("auth.enabled=").append(props.getProperty("auth.enabled", String.valueOf(authEnabled))).append("\n");
        sb.append("auth.username=").append(props.getProperty("auth.username", username)).append("\n");
        sb.append("auth.remember_me_days=").append(props.getProperty("auth.remember_me_days", String.valueOf(rememberMeDays))).append("\n\n");

        sb.append("# After the first site launch, use the command changepassword\n");
        sb.append("auth.password_hash=").append(props.getProperty("auth.password_hash", passwordHash)).append("\n");
        sb.append("auth.password_salt=").append(props.getProperty("auth.password_salt", passwordSalt)).append("\n");

        try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
            writer.write(sb.toString());
        } catch (IOException e) {
            logger.warn("Failed to save config.properties: {}", e.getMessage());
        }
    }

    private int parseInt(String val, int def) {
        try {
            return Integer.parseInt(val.trim());
        } catch (Exception e) {
            return def;
        }
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String channel() {
        return channel;
    }

    public String getTelemetrySecret() {
        return telemetrySecret;
    }

    public boolean isAuthEnabled() {
        return authEnabled;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return "[PROTECTED]";
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getPasswordSalt() {
        return passwordSalt;
    }

    public boolean isFirstLaunch() {
        return firstLaunch;
    }

    public String getGeneratedPassword() {
        return generatedPassword;
    }

    public int getRememberMeDays() {
        return rememberMeDays;
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }
}
