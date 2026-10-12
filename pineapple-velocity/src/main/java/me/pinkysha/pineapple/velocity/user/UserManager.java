package me.pinkysha.pineapple.velocity.user;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.pinkysha.pineapple.velocity.config.VelocityConfig;
import me.pinkysha.pineapple.velocity.security.PasswordSecurity;
import me.pinkysha.pineapple.velocity.server.HttpSecurity;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class UserManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record UserEntry(String passwordHash, String passwordSalt, boolean isSuperAdmin) {}

    private final Path usersFile;
    private final VelocityConfig config;
    private final Logger logger;
    private final Map<String, UserEntry> users = new ConcurrentHashMap<>();
    private volatile Consumer<String> credentialsRevokedListener = username -> {};

    /** Hash checked for unknown users so login timing does not reveal which usernames exist. */
    private static final String DUMMY_SALT = PasswordSecurity.generateSalt();
    private static final String DUMMY_HASH = PasswordSecurity.hashPassword("pineapple-dummy", DUMMY_SALT);

    /**
     * Called with the username whenever its password changes or the account is removed,
     * so existing web sessions can be revoked.
     */
    public void setCredentialsRevokedListener(Consumer<String> listener) {
        this.credentialsRevokedListener = listener != null ? listener : username -> {};
    }

    public UserManager(Path dataDirectory, VelocityConfig config, Logger logger) {
        this.usersFile = dataDirectory.resolve("users.json");
        this.config = config;
        this.logger = logger;
        load();
    }

    public synchronized void load() {
        users.clear();
        try {
            if (Files.exists(usersFile)) {
                try (BufferedReader reader = Files.newBufferedReader(usersFile, StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    if (root.has("users") && root.get("users").isJsonObject()) {
                        JsonObject usersObj = root.getAsJsonObject("users");
                        for (String username : usersObj.keySet()) {
                            if (usersObj.get(username).isJsonObject()) {
                                JsonObject u = usersObj.getAsJsonObject(username);
                                String hash = u.has("password_hash") ? u.get("password_hash").getAsString() : "";
                                String salt = u.has("password_salt") ? u.get("password_salt").getAsString() : "";
                                boolean isSuper = u.has("is_superadmin") && u.get("is_superadmin").getAsBoolean();
                                users.put(username.trim().toLowerCase(), new UserEntry(hash, salt, isSuper));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.warn("Could not read users.json: {}", e.getMessage());
            }
        }

        // Migrate admin from config if not present
        if (!users.containsKey("admin")) {
            String hash = config.getPasswordHash();
            String salt = config.getPasswordSalt();
            users.put("admin", new UserEntry(hash, salt, true));
            save();
        }
    }

    public synchronized void save() {
        try {
            if (!Files.exists(usersFile.getParent())) {
                Files.createDirectories(usersFile.getParent());
            }

            JsonObject root = new JsonObject();
            JsonObject usersObj = new JsonObject();
            for (Map.Entry<String, UserEntry> entry : users.entrySet()) {
                JsonObject u = new JsonObject();
                u.addProperty("password_hash", entry.getValue().passwordHash());
                u.addProperty("password_salt", entry.getValue().passwordSalt());
                u.addProperty("is_superadmin", entry.getValue().isSuperAdmin());
                usersObj.add(entry.getKey(), u);
            }
            root.add("users", usersObj);

            try (BufferedWriter writer = Files.newBufferedWriter(usersFile, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            HttpSecurity.restrictToOwner(usersFile);
        } catch (Exception e) {
            if (logger != null) {
                logger.error("Failed to save users.json: {}", e.getMessage());
            }
        }
    }

    public boolean verifyPassword(String username, String candidatePassword) {
        if (username == null || candidatePassword == null) return false;
        String key = username.trim().toLowerCase();
        UserEntry entry = users.get(key);
        if (entry == null) {
            PasswordSecurity.verifyPassword(candidatePassword, DUMMY_SALT, DUMMY_HASH);
            return false;
        }
        return PasswordSecurity.verifyPassword(candidatePassword, entry.passwordSalt(), entry.passwordHash());
    }

    public synchronized boolean updatePassword(String username, String newPassword) {
        if (username == null || newPassword == null || newPassword.isBlank()) return false;
        String key = username.trim().toLowerCase();
        UserEntry old = users.get(key);
        boolean isSuper = old != null ? old.isSuperAdmin() : "admin".equals(key);

        String newSalt = PasswordSecurity.generateSalt();
        String newHash = PasswordSecurity.hashPassword(newPassword, newSalt);

        users.put(key, new UserEntry(newHash, newSalt, isSuper));
        save();

        if ("admin".equals(key)) {
            config.updatePassword(newPassword);
        }
        credentialsRevokedListener.accept(key);
        return true;
    }

    public synchronized boolean addUser(String username, String password, boolean isSuperAdmin) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) return false;
        String key = username.trim().toLowerCase();
        if (users.containsKey(key)) return false;

        String salt = PasswordSecurity.generateSalt();
        String hash = PasswordSecurity.hashPassword(password, salt);
        users.put(key, new UserEntry(hash, salt, isSuperAdmin));
        save();
        return true;
    }

    public synchronized boolean removeUser(String username) {
        if (username == null) return false;
        String key = username.trim().toLowerCase();
        if ("admin".equalsIgnoreCase(key)) return false; // Prevent removing primary admin
        UserEntry removed = users.remove(key);
        if (removed != null) {
            save();
            credentialsRevokedListener.accept(key);
            return true;
        }
        return false;
    }

    public boolean userExists(String username) {
        if (username == null) return false;
        return users.containsKey(username.trim().toLowerCase());
    }

    public boolean isSuperAdmin(String username) {
        if (username == null) return false;
        String key = username.trim().toLowerCase();
        if ("admin".equals(key)) return true;
        UserEntry entry = users.get(key);
        return entry != null && entry.isSuperAdmin();
    }

    public Map<String, UserEntry> getAllUsers() {
        return Collections.unmodifiableMap(users);
    }
}
