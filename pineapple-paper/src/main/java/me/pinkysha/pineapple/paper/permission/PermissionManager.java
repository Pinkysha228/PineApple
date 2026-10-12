package me.pinkysha.pineapple.paper.permission;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PermissionManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record UserPerm(boolean telemetry, boolean consoleView, boolean consoleCommand) {
        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("telemetry", telemetry);
            obj.addProperty("console_view", consoleView);
            obj.addProperty("console_command", consoleCommand);
            return obj;
        }
    }

    private final Path permissionsFile;
    private final JavaPlugin plugin;
    private final Map<String, UserPerm> permissions = new ConcurrentHashMap<>();

    public PermissionManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.permissionsFile = plugin.getDataFolder().toPath().resolve("permissions.json");
        loadOrCreate();
    }

    public synchronized void reload() {
        loadOrCreate();
    }

    public synchronized void loadOrCreate() {
        try {
            if (!Files.exists(permissionsFile.getParent())) {
                Files.createDirectories(permissionsFile.getParent());
            }

            if (Files.exists(permissionsFile)) {
                try (BufferedReader reader = Files.newBufferedReader(permissionsFile, StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    permissions.clear();
                    for (String username : root.keySet()) {
                        if (root.get(username).isJsonObject()) {
                            JsonObject p = root.getAsJsonObject(username);
                            boolean telem = p.has("telemetry") && p.get("telemetry").getAsBoolean();
                            boolean conView = p.has("console_view") && p.get("console_view").getAsBoolean();
                            boolean conCmd = p.has("console_command") && p.get("console_command").getAsBoolean();
                            permissions.put(username.trim().toLowerCase(), new UserPerm(telem, conView, conCmd));
                        }
                    }
                }
            } else {
                saveDefault();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load permissions.json: " + e.getMessage());
            saveDefault();
        }
    }

    private void saveDefault() {
        permissions.clear();
        permissions.put("admin", new UserPerm(true, true, true));
        permissions.put("moderator", new UserPerm(true, true, false));

        JsonObject root = new JsonObject();
        for (Map.Entry<String, UserPerm> entry : permissions.entrySet()) {
            root.add(entry.getKey(), entry.getValue().toJson());
        }

        try (BufferedWriter writer = Files.newBufferedWriter(permissionsFile, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
            plugin.getLogger().info("Created default permissions.json with admin and moderator templates.");
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save default permissions.json: " + e.getMessage());
        }
    }

    public synchronized boolean setPermission(String username, boolean telemetry, boolean consoleView, boolean consoleCommand) {
        String key = username.trim().toLowerCase();
        permissions.put(key, new UserPerm(telemetry, consoleView, consoleCommand));
        return saveToFile();
    }

    public synchronized boolean removePermission(String username) {
        String key = username.trim().toLowerCase();
        UserPerm removed = permissions.remove(key);
        if (removed != null) {
            return saveToFile();
        }
        return false;
    }

    public synchronized boolean saveToFile() {
        try {
            if (!Files.exists(permissionsFile.getParent())) {
                Files.createDirectories(permissionsFile.getParent());
            }
            JsonObject root = new JsonObject();
            for (Map.Entry<String, UserPerm> entry : permissions.entrySet()) {
                root.add(entry.getKey(), entry.getValue().toJson());
            }
            try (BufferedWriter writer = Files.newBufferedWriter(permissionsFile, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save permissions.json: " + e.getMessage());
            return false;
        }
    }

    public JsonObject getPermissionsJson() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, UserPerm> entry : permissions.entrySet()) {
            root.add(entry.getKey(), entry.getValue().toJson());
        }
        return root;
    }

    public Map<String, UserPerm> getPermissions() {
        return permissions;
    }
}
