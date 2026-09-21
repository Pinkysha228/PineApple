package me.pinkysha.pineapple.paper.server;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe session management with Remember Me support and persistent disk storage.
 */
public class SessionManager {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Gson GSON = new Gson();
    private static final Type SESSIONS_TYPE = new TypeToken<Map<String, SessionInfo>>() {}.getType();

    private final Map<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();
    private final Path storageFile;

    public record SessionInfo(String username, boolean rememberMe, long expiryEpochMs) {
        public boolean isExpired() {
            return System.currentTimeMillis() > expiryEpochMs;
        }
    }

    public SessionManager() {
        this(null);
    }

    public SessionManager(Path dataDirectory) {
        if (dataDirectory != null) {
            this.storageFile = dataDirectory.resolve("sessions.json");
            load();
        } else {
            this.storageFile = null;
        }
    }

    public String createSession(String username, boolean rememberMe, int rememberMeDays) {
        String token = UUID.randomUUID().toString().replace("-", "") + Long.toHexString(RANDOM.nextLong());
        long lifetimeMs;
        if (rememberMe) {
            lifetimeMs = (long) Math.max(1, rememberMeDays) * 24L * 3600L * 1000L;
        } else {
            lifetimeMs = 12L * 3600L * 1000L;
        }
        activeSessions.put(token, new SessionInfo(username, rememberMe, System.currentTimeMillis() + lifetimeMs));
        cleanupExpired();
        if (rememberMe) {
            save();
        }
        return token;
    }

    public boolean isValid(String token) {
        if (token == null || token.isBlank()) return false;
        SessionInfo info = activeSessions.get(token);
        if (info == null) return false;
        if (info.isExpired()) {
            activeSessions.remove(token);
            save();
            return false;
        }
        return true;
    }

    public String getUsername(String token) {
        if (token == null) return null;
        SessionInfo info = activeSessions.get(token);
        if (info != null && !info.isExpired()) {
            return info.username();
        }
        return null;
    }

    public void invalidate(String token) {
        if (token != null && activeSessions.remove(token) != null) {
            save();
        }
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        boolean removed = activeSessions.entrySet().removeIf(entry -> entry.getValue().expiryEpochMs() < now);
        if (removed) {
            save();
        }
    }

    private synchronized void load() {
        if (storageFile == null || !Files.exists(storageFile)) return;
        try (Reader reader = Files.newBufferedReader(storageFile)) {
            Map<String, SessionInfo> loaded = GSON.fromJson(reader, SESSIONS_TYPE);
            if (loaded != null) {
                long now = System.currentTimeMillis();
                for (Map.Entry<String, SessionInfo> entry : loaded.entrySet()) {
                    if (entry.getValue() != null && !entry.getValue().isExpired()) {
                        activeSessions.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private synchronized void save() {
        if (storageFile == null) return;
        try {
            if (storageFile.getParent() != null) {
                Files.createDirectories(storageFile.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(storageFile)) {
                // Only persist sessions with rememberMe = true
                Map<String, SessionInfo> toSave = new ConcurrentHashMap<>();
                for (Map.Entry<String, SessionInfo> entry : activeSessions.entrySet()) {
                    if (entry.getValue().rememberMe() && !entry.getValue().isExpired()) {
                        toSave.put(entry.getKey(), entry.getValue());
                    }
                }
                GSON.toJson(toSave, SESSIONS_TYPE, writer);
            }
        } catch (Exception ignored) {}
    }
}
