package me.pinkysha.pineapple.velocity.server;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Gson GSON = new Gson();
    private static final Type SESSIONS_TYPE = new TypeToken<Map<String, Long>>() {}.getType();

    private final Map<String, Long> sessions = new ConcurrentHashMap<>();
    private final Path storageFile;

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

    public String createSession(int durationDays) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long expiry = System.currentTimeMillis() + (durationDays * 86_400_000L);
        sessions.put(token, expiry);
        save();
        return token;
    }

    public boolean isValid(String token) {
        if (token == null || token.isBlank()) return false;
        Long expiry = sessions.get(token);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            sessions.remove(token);
            save();
            return false;
        }
        return true;
    }

    public void invalidate(String token) {
        if (token != null && sessions.remove(token) != null) {
            save();
        }
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        boolean removed = sessions.entrySet().removeIf(entry -> now > entry.getValue());
        if (removed) {
            save();
        }
    }

    private synchronized void load() {
        if (storageFile == null || !Files.exists(storageFile)) return;
        try (Reader reader = Files.newBufferedReader(storageFile)) {
            Map<String, Long> loaded = GSON.fromJson(reader, SESSIONS_TYPE);
            if (loaded != null) {
                long now = System.currentTimeMillis();
                for (Map.Entry<String, Long> entry : loaded.entrySet()) {
                    if (entry.getValue() != null && entry.getValue() > now) {
                        sessions.put(entry.getKey(), entry.getValue());
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
                GSON.toJson(sessions, SESSIONS_TYPE, writer);
            }
        } catch (Exception ignored) {}
    }
}
