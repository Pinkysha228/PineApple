package me.pinkysha.pineapple.paper.config;

/**
 * Configuration abstraction for PineApple Paper Web Monitor & Telemetry.
 */
public interface MonitorConfig {
    String serverName();
    String channel();
    boolean isStandaloneWebServer();

    boolean isAuthEnabled();
    String getUsername();
    String getPassword();
    int getRememberMeDays();
    int getMaxPlayersDisplay();
    long getCacheTtlMs();
    default boolean isShowAvatars() { return true; }

    void logInfo(String message);
    void logWarning(String message);
    void logSevere(String message);
}
