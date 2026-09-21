package me.pinkysha.pineapple.velocity.config;

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
    private boolean authEnabled = true;
    private String username = "admin";
    private String password = "admin";
    private int rememberMeDays = 30;
    private String defaultLanguage = "en";

    public VelocityConfig(Path dataDirectory, Logger logger) {
        this.configFile = dataDirectory.resolve("config.properties");
        this.logger = logger;
        loadOrCreate();
    }

    public void loadOrCreate() {
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
                this.password = props.getProperty("auth.password", "admin").trim();
                this.rememberMeDays = parseInt(props.getProperty("auth.remember_me_days", "30"), 30);
                this.defaultLanguage = props.getProperty("server.default_language", "en").trim().toLowerCase();
            } else {
                saveDefault();
            }
        } catch (IOException e) {
            logger.warn("Failed to load or create config.properties: {}", e.getMessage());
        }
    }

    private void saveDefault() {
        Properties props = new Properties();
        props.setProperty("server.host", host);
        props.setProperty("server.port", String.valueOf(port));
        props.setProperty("telemetry.channel", channel);
        props.setProperty("auth.enabled", String.valueOf(authEnabled));
        props.setProperty("auth.username", username);
        props.setProperty("auth.password", password);
        props.setProperty("auth.remember_me_days", String.valueOf(rememberMeDays));
        props.setProperty("server.default_language", defaultLanguage);

        try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
            props.store(writer, "PineApple Velocity Proxy Monitor Configuration");
        } catch (IOException e) {
            logger.warn("Failed to save default config.properties: {}", e.getMessage());
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

    public boolean isAuthEnabled() {
        return authEnabled;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public int getRememberMeDays() {
        return rememberMeDays;
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }
}
