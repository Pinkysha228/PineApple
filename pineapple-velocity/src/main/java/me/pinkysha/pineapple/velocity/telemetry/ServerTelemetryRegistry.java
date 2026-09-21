package me.pinkysha.pineapple.velocity.telemetry;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ServerTelemetryRegistry {

    public static final long DEFAULT_TIMEOUT_MS = 10_000L;

    private final Map<String, ServerTelemetryState> servers = new ConcurrentHashMap<>();

    public void registerServer(String serverId) {
        if (serverId != null && !serverId.isBlank()) {
            servers.computeIfAbsent(serverId.toLowerCase(Locale.ROOT), ServerTelemetryState::new);
        }
    }

    public boolean handleIncomingTelemetry(String originServer, String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return false;
        }

        String targetId = originServer;
        try {
            JsonObject root = JsonParser.parseString(rawJson).getAsJsonObject();
            if (root.has("server_id")) {
                String declaredId = root.get("server_id").getAsString();
                if (!declaredId.isBlank()) {
                    targetId = declaredId;
                }
            } else if (targetId == null && root.has("server_name")) {
                targetId = root.get("server_name").getAsString();
            }
        } catch (Exception ignored) {}

        if (targetId == null || targetId.isBlank()) {
            targetId = "unknown";
        }

        String key = targetId.toLowerCase(Locale.ROOT);
        boolean isNew = !servers.containsKey(key);
        ServerTelemetryState state = servers.computeIfAbsent(key, ServerTelemetryState::new);
        boolean wasOffline = !state.isOnline();
        state.updateFromTelemetry(rawJson);
        return isNew || wasOffline;
    }

    public void checkServerTimeouts(long timeoutMs) {
        long now = System.currentTimeMillis();
        for (ServerTelemetryState state : servers.values()) {
            state.checkTimeout(now, timeoutMs);
        }
    }

    public Optional<ServerTelemetryState> getServer(String serverId) {
        if (serverId == null) return Optional.empty();
        return Optional.ofNullable(servers.get(serverId.toLowerCase(Locale.ROOT)));
    }

    public Collection<ServerTelemetryState> getAllServers() {
        return servers.values();
    }

    public String getNetworkOverviewJson() {
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();

        int activeCount = 0;
        int totalPlayers = 0;

        List<ServerTelemetryState> sorted = new ArrayList<>(servers.values());
        sorted.sort(Comparator.comparing(ServerTelemetryState::getServerId));

        for (ServerTelemetryState state : sorted) {
            if (state.isOnline()) {
                activeCount++;
                totalPlayers += state.getOnlinePlayers();
            }
            array.add(state.toOverviewJsonObject());
        }

        root.addProperty("active_count", activeCount);
        root.addProperty("total_count", servers.size());
        root.addProperty("total_players", totalPlayers);
        root.add("servers", array);

        return root.toString();
    }

    public String getServerStatsJson(String serverId) {
        if (serverId != null && !serverId.isBlank()) {
            ServerTelemetryState state = servers.get(serverId.toLowerCase(Locale.ROOT));
            if (state != null) {
                return state.getLatestFullJson();
            }
        }
        // Fallback to first available server or empty
        if (!servers.isEmpty()) {
            return servers.values().iterator().next().getLatestFullJson();
        }
        return "{\"online\":false,\"error\":\"No servers registered\"}";
    }

    public String getServerHistoryJson(String serverId) {
        if (serverId != null && !serverId.isBlank()) {
            ServerTelemetryState state = servers.get(serverId.toLowerCase(Locale.ROOT));
            if (state != null) {
                return state.getHistoryJson();
            }
        }
        if (!servers.isEmpty()) {
            return servers.values().iterator().next().getHistoryJson();
        }
        return "[]";
    }
}
