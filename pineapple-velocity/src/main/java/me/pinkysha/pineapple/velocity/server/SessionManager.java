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

    public static class SessionRecord {
        private String username;
        private long expiry;

        public SessionRecord() {}

        public SessionRecord(String username, long expiry) {
            this.username = username;
            this.expiry = expiry;
        }

        public String getUsername() {
            return username != null ? username : "admin";
        }

        public long getExpiry() {
            return expiry;
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Gson GSON = new Gson();
    private static final Type SESSIONS_TYPE = new TypeToken<Map<String, SessionRecord>>() {}.getType();

    private final Map<String, SessionRecord> sessions = new ConcurrentHashMap<>();
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
        return createSession("admin", durationDays);
    }

    public String createSession(String username, int durationDays) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long expiry = System.currentTimeMillis() + (durationDays * 86_400_000L);
        String uname = (username != null && !username.isBlank()) ? username.trim() : "admin";
        sessions.put(token, new SessionRecord(uname, expiry));
        save();
        return token;
    }

    public boolean isValid(String token) {
        if (token == null || token.isBlank()) return false;
        SessionRecord rec = sessions.get(token);
        if (rec == null) return false;
        if (System.currentTimeMillis() > rec.getExpiry()) {
            sessions.remove(token);
            save();
            return false;
        }
        return true;
    }

    public String getUsername(String token) {
        if (!isValid(token)) return null;
        SessionRecord rec = sessions.get(token);
        return rec != null ? rec.getUsername() : "admin";
    }

    public void invalidate(String token) {
        if (token != null && sessions.remove(token) != null) {
            save();
        }
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        boolean removed = sessions.entrySet().removeIf(entry -> now > entry.getValue().getExpiry());
        if (removed) {
            save();
        }
    }

    private synchronized void load() {
        if (storageFile == null || !Files.exists(storageFile)) return;
        try (Reader reader = Files.newBufferedReader(storageFile)) {
            Map<String, SessionRecord> loaded = GSON.fromJson(reader, SESSIONS_TYPE);
            if (loaded != null) {
                long now = System.currentTimeMillis();
                for (Map.Entry<String, SessionRecord> entry : loaded.entrySet()) {
                    if (entry.getValue() != null && entry.getValue().getExpiry() > now) {
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
