package me.pinkysha.pineapple.velocity.telemetry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayDeque;
import java.util.Deque;

public class ServerTelemetryState {

    public static final int MAX_HISTORY_POINTS = 1440;

    private final String serverId;
    private String serverName;
    private boolean online;
    private long lastSeen;

    private String version = "Unknown";
    private String javaVersion = "Unknown";
    private String uptime = "0s";

    private double tps = 20.0;
    private double mspt = 0.0;
    private double tps1m = 20.0;
    private double tps5m = 20.0;
    private double tps15m = 20.0;

    private long ramUsedMb = 0;
    private long ramCommittedMb = 0;
    private long ramMaxMb = 0;
    private int ramPct = 0;
    private long sysTotalMb = 0;

    private double cpuProcessPct = 0.0;
    private double cpuSystemPct = 0.0;
    private int cpuCores = 1;

    private int totalChunks = 0;
    private int totalEntities = 0;
    private int onlinePlayers = 0;
    private int maxPlayers = 20;

    private String latestFullJson;
    private final Deque<HistoryPoint> historyBuffer = new ArrayDeque<>(MAX_HISTORY_POINTS);

    public record HistoryPoint(long timestamp, double tps, double mspt, long ramUsed, long ramMax, int ramPct, double cpuProcess) {}

    public ServerTelemetryState(String serverId) {
        this.serverId = serverId;
        this.serverName = serverId;
        this.online = false;
        this.lastSeen = 0;
    }

    public synchronized void updateFromTelemetry(String jsonPayload) {
        try {
            JsonObject obj = JsonParser.parseString(jsonPayload).getAsJsonObject();
            this.lastSeen = System.currentTimeMillis();
            this.online = true;
            this.latestFullJson = jsonPayload;

            if (obj.has("server_name")) {
                this.serverName = obj.get("server_name").getAsString();
            }
            if (obj.has("server")) {
                if (obj.get("server").isJsonObject()) {
                    JsonObject srvObj = obj.getAsJsonObject("server");
                    if (srvObj.has("name")) this.serverName = srvObj.get("name").getAsString();
                    if (srvObj.has("version")) this.version = srvObj.get("version").getAsString();
                    if (srvObj.has("java_version")) this.javaVersion = srvObj.get("java_version").getAsString();
                } else {
                    this.serverName = obj.get("server").getAsString();
                }
            }
            if (obj.has("version")) {
                this.version = obj.get("version").getAsString();
            }
            if (obj.has("java_version")) {
                this.javaVersion = obj.get("java_version").getAsString();
            }
            if (obj.has("system") && obj.get("system").isJsonObject()) {
                JsonObject sysObj = obj.getAsJsonObject("system");
                if (sysObj.has("uptime")) this.uptime = sysObj.get("uptime").getAsString();
            }
            if (obj.has("uptime")) {
                this.uptime = obj.get("uptime").getAsString();
            }

            if (obj.has("tps") && obj.get("tps").isJsonObject()) {
                JsonObject tpsObj = obj.getAsJsonObject("tps");
                this.tps = tpsObj.has("current") ? tpsObj.get("current").getAsDouble() : 20.0;
                this.mspt = tpsObj.has("mspt") ? tpsObj.get("mspt").getAsDouble() : 0.0;
                if (tpsObj.has("history") && tpsObj.get("history").isJsonArray()) {
                    JsonArray arr = tpsObj.getAsJsonArray("history");
                    if (arr.size() > 0) this.tps1m = arr.get(0).getAsDouble();
                    if (arr.size() > 1) this.tps5m = arr.get(1).getAsDouble();
                    if (arr.size() > 2) this.tps15m = arr.get(2).getAsDouble();
                }
                if (tpsObj.has("1m")) this.tps1m = tpsObj.get("1m").getAsDouble();
                if (tpsObj.has("5m")) this.tps5m = tpsObj.get("5m").getAsDouble();
                if (tpsObj.has("15m")) this.tps15m = tpsObj.get("15m").getAsDouble();
            }

            if (obj.has("memory") && obj.get("memory").isJsonObject()) {
                JsonObject memObj = obj.getAsJsonObject("memory");
                this.ramUsedMb = memObj.has("used_mb") ? memObj.get("used_mb").getAsLong()
                        : (memObj.has("heap_used_mb") ? memObj.get("heap_used_mb").getAsLong() : 0);
                this.ramCommittedMb = memObj.has("committed_mb") ? memObj.get("committed_mb").getAsLong()
                        : (memObj.has("heap_committed_mb") ? memObj.get("heap_committed_mb").getAsLong() : 0);
                this.ramMaxMb = memObj.has("max_mb") ? memObj.get("max_mb").getAsLong()
                        : (memObj.has("heap_max_mb") ? memObj.get("heap_max_mb").getAsLong() : 0);
                this.ramPct = memObj.has("percentage") ? memObj.get("percentage").getAsInt()
                        : (memObj.has("heap_percent") ? memObj.get("heap_percent").getAsInt() : (this.ramMaxMb > 0 ? (int) Math.round(((double) this.ramUsedMb / this.ramMaxMb) * 100.0) : 0));
                this.sysTotalMb = memObj.has("system_total_mb") ? memObj.get("system_total_mb").getAsLong() : 0;
            }

            if (obj.has("cpu") && obj.get("cpu").isJsonObject()) {
                JsonObject cpuObj = obj.getAsJsonObject("cpu");
                this.cpuProcessPct = cpuObj.has("process_percentage") ? cpuObj.get("process_percentage").getAsDouble()
                        : (cpuObj.has("process_percent") ? cpuObj.get("process_percent").getAsDouble() : 0.0);
                this.cpuSystemPct = cpuObj.has("system_percentage") ? cpuObj.get("system_percentage").getAsDouble()
                        : (cpuObj.has("system_percent") ? cpuObj.get("system_percent").getAsDouble() : 0.0);
                this.cpuCores = cpuObj.has("cores") ? cpuObj.get("cores").getAsInt()
                        : (cpuObj.has("available_processors") ? cpuObj.get("available_processors").getAsInt() : 1);
            }

            if (obj.has("worlds")) {
                int chunks = 0;
                int entities = 0;
                if (obj.get("worlds").isJsonObject()) {
                    JsonObject wObj = obj.getAsJsonObject("worlds");
                    if (wObj.has("total_chunks")) chunks = wObj.get("total_chunks").getAsInt();
                    if (wObj.has("total_entities")) entities = wObj.get("total_entities").getAsInt();
                    if (chunks == 0 && entities == 0 && wObj.has("list") && wObj.get("list").isJsonArray()) {
                        for (JsonElement w : wObj.getAsJsonArray("list")) {
                            if (w.isJsonObject()) {
                                JsonObject wo = w.getAsJsonObject();
                                chunks += wo.has("loaded_chunks") ? wo.get("loaded_chunks").getAsInt() : (wo.has("chunks") ? wo.get("chunks").getAsInt() : 0);
                                entities += wo.has("entities") ? wo.get("entities").getAsInt() : 0;
                            }
                        }
                    }
                } else if (obj.get("worlds").isJsonArray()) {
                    for (JsonElement w : obj.getAsJsonArray("worlds")) {
                        if (w.isJsonObject()) {
                            JsonObject wo = w.getAsJsonObject();
                            chunks += wo.has("loaded_chunks") ? wo.get("loaded_chunks").getAsInt() : (wo.has("chunks") ? wo.get("chunks").getAsInt() : 0);
                            entities += wo.has("entities") ? wo.get("entities").getAsInt() : 0;
                        }
                    }
                }
                this.totalChunks = chunks;
                this.totalEntities = entities;
            }

            if (obj.has("players") && obj.get("players").isJsonObject()) {
                JsonObject pl = obj.getAsJsonObject("players");
                this.onlinePlayers = pl.has("online") ? pl.get("online").getAsInt() : 0;
                this.maxPlayers = pl.has("max") ? pl.get("max").getAsInt() : 20;
            }

            // Append to ring buffer
            addHistoryPoint(new HistoryPoint(this.lastSeen, this.tps, this.mspt, this.ramUsedMb, this.ramMaxMb, this.ramPct, this.cpuProcessPct));
        } catch (Exception e) {
            // Ignore parse errors on malformed packets
        }
    }

    public synchronized void addHistoryPoint(HistoryPoint point) {
        if (historyBuffer.size() >= MAX_HISTORY_POINTS) {
            historyBuffer.removeFirst();
        }
        historyBuffer.addLast(point);
    }

    public synchronized boolean checkTimeout(long now, long timeoutMillis) {
        if (online && (now - lastSeen > timeoutMillis)) {
            this.online = false;
            return true;
        }
        return false;
    }

    public String getServerId() {
        return serverId;
    }

    public synchronized String getServerName() {
        return serverName;
    }

    public synchronized boolean isOnline() {
        return online;
    }

    public synchronized void setOnline(boolean online) {
        this.online = online;
    }

    public synchronized long getLastSeen() {
        return lastSeen;
    }

    public synchronized double getTps() {
        return tps;
    }

    public synchronized double getMspt() {
        return mspt;
    }

    public synchronized long getRamUsedMb() {
        return ramUsedMb;
    }

    public synchronized long getRamMaxMb() {
        return ramMaxMb;
    }

    public synchronized int getRamPct() {
        return ramPct;
    }

    public synchronized double getCpuProcessPct() {
        return cpuProcessPct;
    }

    public synchronized String getVersion() {
        return version;
    }

    public synchronized int getOnlinePlayers() {
        return onlinePlayers;
    }

    public synchronized int getMaxPlayers() {
        return maxPlayers;
    }

    public synchronized String getLatestFullJson() {
        if (latestFullJson != null) {
            return latestFullJson;
        }
        // Fallback minimal JSON when not yet connected
        return "{\"server_id\":\"" + serverId + "\",\"server_name\":\"" + serverName + "\",\"online\":" + online +
                ",\"version\":\"" + version + "\",\"java_version\":\"" + javaVersion + "\",\"uptime\":\"0s\"," +
                "\"server\":{\"id\":\"" + serverId + "\",\"name\":\"" + serverName + "\",\"version\":\"" + version + "\",\"java_version\":\"" + javaVersion + "\"}," +
                "\"system\":{\"uptime\":\"0s\"}," +
                "\"tps\":{\"current\":" + tps + ",\"mspt\":" + mspt + ",\"1m\":" + tps1m + ",\"5m\":" + tps5m + ",\"15m\":" + tps15m + ",\"history\":[" + tps + "," + tps + "," + tps + "]}," +
                "\"memory\":{\"used_mb\":" + ramUsedMb + ",\"heap_used_mb\":" + ramUsedMb + ",\"committed_mb\":" + ramCommittedMb + ",\"heap_committed_mb\":" + ramCommittedMb + ",\"max_mb\":" + ramMaxMb + ",\"heap_max_mb\":" + ramMaxMb + ",\"percentage\":" + ramPct + ",\"heap_percent\":" + ramPct + ",\"system_total_mb\":" + sysTotalMb + "}," +
                "\"cpu\":{\"process_percentage\":" + cpuProcessPct + ",\"process_percent\":" + cpuProcessPct + ",\"system_percentage\":" + cpuSystemPct + ",\"system_percent\":" + cpuSystemPct + ",\"cores\":" + cpuCores + ",\"available_processors\":" + cpuCores + "}," +
                "\"worlds\":{\"total_chunks\":" + totalChunks + ",\"total_entities\":" + totalEntities + ",\"list\":[]}," +
                "\"players\":{\"online\":" + onlinePlayers + ",\"max\":" + maxPlayers + ",\"list\":[]}}";
    }

    public synchronized String getHistoryJson() {
        if (historyBuffer.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder(historyBuffer.size() * 90 + 4);
        sb.append('[');
        boolean first = true;
        for (HistoryPoint hp : historyBuffer) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"t\":").append(hp.timestamp())
              .append(",\"timestamp\":").append(hp.timestamp())
              .append(",\"tps\":").append(hp.tps())
              .append(",\"mspt\":").append(hp.mspt())
              .append(",\"ram\":").append(hp.ramUsed())
              .append(",\"ram_used_mb\":").append(hp.ramUsed())
              .append(",\"ram_max\":").append(hp.ramMax())
              .append(",\"ram_max_mb\":").append(hp.ramMax())
              .append(",\"ram_pct\":").append(hp.ramPct())
              .append(",\"cpu\":").append(hp.cpuProcess())
              .append(",\"cpu_pct\":").append(hp.cpuProcess())
              .append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    public synchronized JsonObject toOverviewJsonObject() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", serverId);
        obj.addProperty("server_id", serverId);
        obj.addProperty("name", serverName);
        obj.addProperty("server_name", serverName);
        obj.addProperty("online", online);
        obj.addProperty("version", version);
        obj.addProperty("tps", Math.round(tps * 10.0) / 10.0);
        obj.addProperty("mspt", Math.round(mspt * 10.0) / 10.0);
        obj.addProperty("ram_used_mb", ramUsedMb);
        obj.addProperty("ram_max_mb", ramMaxMb);
        double ramGb = Math.round((ramUsedMb / 1024.0) * 10.0) / 10.0;
        obj.addProperty("ram_used_gb", ramGb);
        double maxRamGb = Math.round((ramMaxMb / 1024.0) * 10.0) / 10.0;
        obj.addProperty("ram_max_gb", maxRamGb);
        obj.addProperty("ram_pct", ramPct);
        obj.addProperty("ram_percent", ramPct);
        double cpuVal = Math.round(cpuProcessPct * 10.0) / 10.0;
        obj.addProperty("cpu_pct", cpuVal);
        obj.addProperty("cpu_percent", cpuVal);
        obj.addProperty("players_count", onlinePlayers);
        obj.addProperty("max_players", maxPlayers);
        obj.addProperty("chunks", totalChunks);
        obj.addProperty("total_chunks", totalChunks);
        obj.addProperty("entities", totalEntities);
        obj.addProperty("total_entities", totalEntities);
        long now = System.currentTimeMillis();
        obj.addProperty("last_seen_ago_ms", lastSeen > 0 ? (now - lastSeen) : -1);
        return obj;
    }
}
