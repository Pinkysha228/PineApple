package me.pinkysha.pineapple.paper.metrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.management.OperatingSystemMXBean;
import me.pinkysha.pineapple.paper.config.MonitorConfig;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Ultra-lightweight Minecraft and JVM resource collector for Paper.
 * Implements server-side ring-buffered history and strict response caching.
 */
public class StatsCollector {

    public record WorldData(String name, String environment, int loadedChunks, int entities) {}
    public record PlayerData(String name, String world, String gamemode, int ping) {}
    public record BukkitSnapshot(
            int totalChunks,
            int totalEntities,
            List<WorldData> worlds,
            int onlineCount,
            int maxPlayers,
            List<PlayerData> players
    ) {}

    public record HistoryPoint(
            long time,
            double tps,
            double mspt,
            long ramMb,
            long ramMaxMb,
            int ramPct,
            double cpuPct
    ) {}

    public static final int MAX_HISTORY_POINTS = 1440; // 1 hour at 2.5s intervals
    private final ArrayDeque<HistoryPoint> historyBuffer = new ArrayDeque<>(MAX_HISTORY_POINTS);
    private final Object historyLock = new Object();
    private final Object fileLock = new Object();
    private volatile long lastHistorySampleTime = 0L;

    private final MonitorConfig config;
    private final OperatingSystemMXBean osBean;
    private final long serverStartTime;

    private volatile BukkitSnapshot latestSnapshot = new BukkitSnapshot(0, 0, Collections.emptyList(), 0, 20, Collections.emptyList());
    private volatile String cachedJson = "{}";
    private volatile long lastCacheTime = 0L;
    private long cacheTtlMs = 1500L;

    public StatsCollector(MonitorConfig config) {
        this.config = config;
        this.serverStartTime = System.currentTimeMillis();
        OperatingSystemMXBean bean = null;
        try {
            var mx = ManagementFactory.getOperatingSystemMXBean();
            if (mx instanceof OperatingSystemMXBean sunMx) {
                bean = sunMx;
            }
        } catch (Throwable ignored) {}
        this.osBean = bean;
    }

    public void setCacheTtlMs(long cacheTtlMs) {
        this.cacheTtlMs = Math.max(500L, cacheTtlMs);
    }

    /**
     * Safely refreshed from the Bukkit main thread via scheduler.
     * Prevents Paper AsyncCatcher exceptions when accessing entities and chunks.
     */
    public void refreshBukkitSnapshot() {
        try {
            if (!Bukkit.isPrimaryThread()) return;
        } catch (Throwable t) {
            return;
        }

        int totalChunks = 0;
        int totalEntities = 0;
        List<WorldData> worldsList = new ArrayList<>();
        try {
            var worlds = Bukkit.getWorlds();
            for (World w : worlds) {
                int chunks = 0;
                try {
                    chunks = w.getLoadedChunks().length;
                } catch (Throwable ignored) {}

                int entities = 0;
                try {
                    entities = w.getEntityCount();
                } catch (Throwable ignored) {}

                totalChunks += chunks;
                totalEntities += entities;
                worldsList.add(new WorldData(
                        w.getName(),
                        w.getEnvironment().name(),
                        chunks,
                        entities
                ));
            }
        } catch (Throwable ignored) {}

        List<PlayerData> playersList = new ArrayList<>();
        int onlineCount = 0;
        int maxPlayers = 20;
        try {
            Collection<? extends Player> online = Bukkit.getOnlinePlayers();
            onlineCount = online.size();
            maxPlayers = Bukkit.getMaxPlayers();
            int displayLimit = config != null ? config.getMaxPlayersDisplay() : 12;
            int count = 0;
            for (Player p : online) {
                if (count++ >= displayLimit) break;
                int ping = 0;
                try {
                    ping = p.getPing();
                } catch (Throwable ignored) {}
                String worldName = p.getWorld().getName();
                String gamemode = p.getGameMode().name();
                playersList.add(new PlayerData(p.getName(), worldName, gamemode, ping));
            }
        } catch (Throwable ignored) {}

        this.latestSnapshot = new BukkitSnapshot(totalChunks, totalEntities, worldsList, onlineCount, maxPlayers, playersList);
    }

    /**
     * Autonomous metric sampler.
     * Invoked periodically by server scheduler (every 2.5 seconds).
     */
    public HistoryPoint recordHistoryPoint() {
        long now = System.currentTimeMillis();
        if (now - lastHistorySampleTime < 2000L && lastHistorySampleTime != 0L) {
            synchronized (historyLock) {
                return historyBuffer.peekLast();
            }
        }
        lastHistorySampleTime = now;

        double[] tpsArr = new double[]{20.0, 20.0, 20.0};
        try {
            tpsArr = Bukkit.getTPS();
        } catch (Throwable ignored) {}
        double tps = Math.clamp(tpsArr.length > 0 ? tpsArr[0] : 20.0, 0.0, 20.0);

        double mspt = 0.0;
        try {
            mspt = Bukkit.getAverageTickTime();
        } catch (Throwable ignored) {}

        Runtime rt = Runtime.getRuntime();
        long totalMem = rt.totalMemory();
        long freeMem = rt.freeMemory();
        long maxMem = rt.maxMemory();
        long usedMem = totalMem - freeMem;
        long usedMb = usedMem / (1024 * 1024);
        long maxMb = maxMem / (1024 * 1024);
        int heapPercent = maxMem > 0 ? (int) Math.round(((double) usedMem / maxMem) * 100.0) : 0;

        double processCpu = 0.0;
        if (osBean != null) {
            try {
                double pc = osBean.getProcessCpuLoad();
                if (pc >= 0) processCpu = Math.round(pc * 1000.0) / 10.0;
            } catch (Throwable ignored) {}
        }

        HistoryPoint point = new HistoryPoint(now, tps, mspt, usedMb, maxMb, heapPercent, processCpu);
        synchronized (historyLock) {
            if (historyBuffer.size() >= MAX_HISTORY_POINTS) {
                historyBuffer.pollFirst();
            }
            historyBuffer.addLast(point);
        }
        return point;
    }

    public void addHistoryPoint(HistoryPoint point) {
        if (point == null) return;
        synchronized (historyLock) {
            if (historyBuffer.size() >= MAX_HISTORY_POINTS) {
                historyBuffer.pollFirst();
            }
            historyBuffer.addLast(point);
            lastHistorySampleTime = point.time();
        }
    }

    public int getHistoryBufferSize() {
        synchronized (historyLock) {
            return historyBuffer.size();
        }
    }

    public void saveHistoryToFile(File file) {
        if (file == null) return;
        List<HistoryPoint> points;
        synchronized (historyLock) {
            if (historyBuffer.isEmpty()) return;
            points = new ArrayList<>(historyBuffer);
        }

        synchronized (fileLock) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                boolean created = parent.mkdirs();
                if (!created && !parent.exists()) {
                    if (config != null) {
                        config.logWarning("Failed to create parent directory for " + file.getAbsolutePath());
                    }
                }
            }

            File tempFile = new File(file.getAbsolutePath() + ".tmp");
            try {
                String json = serializeHistoryPoints(points);
                Files.writeString(tempFile.toPath(), json, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                try {
                    Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Throwable t) {
                if (config != null) {
                    config.logWarning("Failed to persist history to " + file.getName() + ": " + t.getMessage());
                }
                try {
                    if (tempFile.exists()) {
                        boolean deleted = tempFile.delete();
                        if (!deleted && config != null) {
                            config.logWarning("Failed to delete temp file " + tempFile.getName());
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    public void loadHistoryFromFile(File file) {
        if (file == null || !file.exists() || file.length() == 0) {
            return;
        }
        synchronized (fileLock) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
                JsonElement root = JsonParser.parseReader(reader);
                if (root != null && root.isJsonArray()) {
                    JsonArray arr = root.getAsJsonArray();
                    List<HistoryPoint> loaded = new ArrayList<>(arr.size());
                    for (JsonElement el : arr) {
                        if (el.isJsonObject()) {
                            JsonObject obj = el.getAsJsonObject();
                            long t = obj.has("t") ? obj.get("t").getAsLong() : 0L;
                            double tps = obj.has("tps") ? obj.get("tps").getAsDouble() : 20.0;
                            double mspt = obj.has("mspt") ? obj.get("mspt").getAsDouble() : 0.0;
                            long ram = obj.has("ram") ? obj.get("ram").getAsLong() : 0L;
                            long ramMax = obj.has("ram_max") ? obj.get("ram_max").getAsLong() : 1024L;
                            int ramPct = obj.has("ram_pct") ? obj.get("ram_pct").getAsInt() : 0;
                            double cpu = obj.has("cpu") ? obj.get("cpu").getAsDouble() : 0.0;
                            if (t > 0) {
                                loaded.add(new HistoryPoint(t, tps, mspt, ram, ramMax, ramPct, cpu));
                            }
                        }
                    }
                    loaded.sort(Comparator.comparingLong(HistoryPoint::time));
                    synchronized (historyLock) {
                        historyBuffer.clear();
                        int start = Math.max(0, loaded.size() - MAX_HISTORY_POINTS);
                        for (int i = start; i < loaded.size(); i++) {
                            historyBuffer.addLast(loaded.get(i));
                        }
                        if (!historyBuffer.isEmpty()) {
                            lastHistorySampleTime = historyBuffer.peekLast().time();
                        }
                    }
                    if (config != null) {
                        config.logInfo("Loaded " + historyBuffer.size() + " telemetry history points from " + file.getName());
                    }
                }
            } catch (Throwable t) {
                if (config != null) {
                    config.logWarning("Failed to load history from " + file.getName() + ": " + t.getMessage());
                }
            }
        }
    }

    public String getHistoryJson() {
        if (getHistoryBufferSize() == 0) {
            recordHistoryPoint();
        }
        List<HistoryPoint> points;
        synchronized (historyLock) {
            points = new ArrayList<>(historyBuffer);
        }
        return serializeHistoryPoints(points);
    }

    private String serializeHistoryPoints(List<HistoryPoint> points) {
        StringBuilder sb = new StringBuilder(points.size() * 120 + 32);
        sb.append("[");
        for (int i = 0; i < points.size(); i++) {
            if (i > 0) sb.append(",");
            HistoryPoint p = points.get(i);
            sb.append("{\"t\":").append(p.time())
              .append(",\"timestamp\":").append(p.time())
              .append(",\"tps\":").append(String.format(Locale.US, "%.2f", p.tps()))
              .append(",\"mspt\":").append(String.format(Locale.US, "%.1f", p.mspt()))
              .append(",\"ram\":").append(p.ramMb())
              .append(",\"ram_used_mb\":").append(p.ramMb())
              .append(",\"ram_max\":").append(p.ramMaxMb())
              .append(",\"ram_max_mb\":").append(p.ramMaxMb())
              .append(",\"ram_pct\":").append(p.ramPct())
              .append(",\"cpu\":").append(String.format(Locale.US, "%.1f", p.cpuPct()))
              .append(",\"cpu_pct\":").append(String.format(Locale.US, "%.1f", p.cpuPct()))
              .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    public String getStatsJson() {
        long now = System.currentTimeMillis();
        if (now - lastCacheTime < cacheTtlMs && cachedJson != null && !cachedJson.equals("{}")) {
            return cachedJson;
        }

        String json = buildStatsJson();
        this.cachedJson = json;
        this.lastCacheTime = now;
        return json;
    }

    public String buildStatsJson() {
        StringBuilder sb = new StringBuilder(1600);
        long now = System.currentTimeMillis();

        String serverId = config != null ? config.serverName() : "vanilla";

        // 1. TPS & MSPT
        double[] tpsArr = new double[]{20.0, 20.0, 20.0};
        try {
            tpsArr = Bukkit.getTPS();
        } catch (Throwable ignored) {}

        double tps1 = Math.clamp(tpsArr.length > 0 ? tpsArr[0] : 20.0, 0.0, 20.0);
        double tps5 = Math.clamp(tpsArr.length > 1 ? tpsArr[1] : 20.0, 0.0, 20.0);
        double tps15 = Math.clamp(tpsArr.length > 2 ? tpsArr[2] : 20.0, 0.0, 20.0);

        double mspt = 0.0;
        try {
            mspt = Bukkit.getAverageTickTime();
        } catch (Throwable ignored) {}

        // 2. JVM Memory
        Runtime rt = Runtime.getRuntime();
        long totalMem = rt.totalMemory();
        long freeMem = rt.freeMemory();
        long maxMem = rt.maxMemory();
        long usedMem = totalMem - freeMem;

        long usedMb = usedMem / (1024 * 1024);
        long maxMb = maxMem / (1024 * 1024);
        long committedMb = totalMem / (1024 * 1024);
        int heapPercent = maxMem > 0 ? (int) Math.round(((double) usedMem / maxMem) * 100.0) : 0;

        // 3. Host System CPU & RAM
        double processCpu = 0.0;
        double systemCpu = 0.0;
        int availableProcessors = rt.availableProcessors();
        long sysTotalMb = 0;
        long sysFreeMb = 0;
        long sysAvailMb = 0;
        long sysUsedMb = 0;

        long[] linuxMem = getLinuxMemoryMb();
        if (linuxMem != null) {
            sysTotalMb = linuxMem[0];
            sysAvailMb = linuxMem[1];
            sysUsedMb = Math.max(0, sysTotalMb - sysAvailMb);
            sysFreeMb = sysAvailMb;
        } else if (osBean != null) {
            try {
                sysTotalMb = osBean.getTotalMemorySize() / (1024 * 1024);
                sysFreeMb = osBean.getFreeMemorySize() / (1024 * 1024);
                sysAvailMb = sysFreeMb;
                sysUsedMb = Math.max(0, sysTotalMb - sysAvailMb);
            } catch (Throwable ignored) {}
        }

        if (osBean != null) {
            try {
                double pc = osBean.getProcessCpuLoad();
                if (pc >= 0) processCpu = Math.round(pc * 1000.0) / 10.0;

                double sc = osBean.getCpuLoad();
                if (sc >= 0) systemCpu = Math.round(sc * 1000.0) / 10.0;
            } catch (Throwable ignored) {}
        }

        // 4. Server Uptime
        long uptimeSec = (now - serverStartTime) / 1000;
        long hours = uptimeSec / 3600;
        long rem = uptimeSec % 3600;
        long mins = rem / 60;
        long secs = rem % 60;
        String uptimeStr = hours + "h " + mins + "m " + secs + "s";

        // 5. Worlds & Chunks
        BukkitSnapshot snapshot = this.latestSnapshot;
        int totalChunks = snapshot.totalChunks();
        int totalEntities = snapshot.totalEntities();
        StringBuilder worldsSb = new StringBuilder();
        for (WorldData w : snapshot.worlds()) {
            if (!worldsSb.isEmpty()) worldsSb.append(",");
            worldsSb.append("{")
                    .append("\"name\":\"").append(escapeJson(w.name())).append("\",")
                    .append("\"environment\":\"").append(escapeJson(w.environment())).append("\",")
                    .append("\"chunks\":").append(w.loadedChunks()).append(",")
                    .append("\"loaded_chunks\":").append(w.loadedChunks()).append(",")
                    .append("\"entities\":").append(w.entities())
                    .append("}");
        }

        // 6. Online Players
        int onlineCount = snapshot.onlineCount();
        int maxPlayers = snapshot.maxPlayers();
        StringBuilder playersSb = new StringBuilder();
        for (PlayerData p : snapshot.players()) {
            if (!playersSb.isEmpty()) playersSb.append(",");
            playersSb.append("{")
                    .append("\"name\":\"").append(escapeJson(p.name())).append("\",")
                    .append("\"world\":\"").append(escapeJson(p.world())).append("\",")
                    .append("\"gamemode\":\"").append(escapeJson(p.gamemode())).append("\",")
                    .append("\"ping\":").append(p.ping())
                    .append("}");
        }

        String serverVersion = "Paper 1.21.4";
        try {
            serverVersion = Bukkit.getName() + " " + Bukkit.getMinecraftVersion();
        } catch (Throwable ignored) {}

        boolean showAvatars = config != null && config.isShowAvatars();

        // Build compact JSON root with server_id and server_name included
        sb.append("{")
                .append("\"timestamp\":").append(now / 1000).append(",")
                .append("\"server_id\":\"").append(escapeJson(serverId)).append("\",")
                .append("\"server_name\":\"").append(escapeJson(serverId)).append("\",")
                .append("\"version\":\"").append(escapeJson(serverVersion)).append("\",")
                .append("\"java_version\":\"").append(escapeJson(System.getProperty("java.version"))).append("\",")
                .append("\"uptime\":\"").append(uptimeStr).append("\",")
                .append("\"config\":{")
                .append("\"show_avatars\":").append(showAvatars)
                .append("},")
                .append("\"server\":{")
                .append("\"id\":\"").append(escapeJson(serverId)).append("\",")
                .append("\"name\":\"").append(escapeJson(serverId)).append("\",")
                .append("\"version\":\"").append(escapeJson(serverVersion)).append("\",")
                .append("\"java_version\":\"").append(escapeJson(System.getProperty("java.version"))).append("\"")
                .append("},")
                .append("\"system\":{")
                .append("\"uptime\":\"").append(uptimeStr).append("\"")
                .append("},")
                .append("\"tps\":{")
                .append("\"current\":").append(String.format(Locale.US, "%.2f", tps1)).append(",")
                .append("\"mspt\":").append(String.format(Locale.US, "%.1f", mspt)).append(",")
                .append("\"1m\":").append(String.format(Locale.US, "%.2f", tps1)).append(",")
                .append("\"5m\":").append(String.format(Locale.US, "%.2f", tps5)).append(",")
                .append("\"15m\":").append(String.format(Locale.US, "%.2f", tps15)).append(",")
                .append("\"history\":[")
                .append(String.format(Locale.US, "%.2f", tps1)).append(",")
                .append(String.format(Locale.US, "%.2f", tps5)).append(",")
                .append(String.format(Locale.US, "%.2f", tps15))
                .append("]")
                .append("},")
                .append("\"memory\":{")
                .append("\"used_mb\":").append(usedMb).append(",")
                .append("\"heap_used_mb\":").append(usedMb).append(",")
                .append("\"max_mb\":").append(maxMb).append(",")
                .append("\"heap_max_mb\":").append(maxMb).append(",")
                .append("\"committed_mb\":").append(committedMb).append(",")
                .append("\"heap_committed_mb\":").append(committedMb).append(",")
                .append("\"percentage\":").append(heapPercent).append(",")
                .append("\"heap_percent\":").append(heapPercent).append(",")
                .append("\"system_total_mb\":").append(sysTotalMb).append(",")
                .append("\"system_available_mb\":").append(sysAvailMb).append(",")
                .append("\"system_used_mb\":").append(sysUsedMb).append(",")
                .append("\"system_free_mb\":").append(sysFreeMb)
                .append("},")
                .append("\"cpu\":{")
                .append("\"process_percentage\":").append(String.format(Locale.US, "%.1f", processCpu)).append(",")
                .append("\"process_percent\":").append(String.format(Locale.US, "%.1f", processCpu)).append(",")
                .append("\"system_percentage\":").append(String.format(Locale.US, "%.1f", systemCpu)).append(",")
                .append("\"system_percent\":").append(String.format(Locale.US, "%.1f", systemCpu)).append(",")
                .append("\"cores\":").append(availableProcessors).append(",")
                .append("\"available_processors\":").append(availableProcessors)
                .append("},")
                .append("\"worlds\":{")
                .append("\"total_chunks\":").append(totalChunks).append(",")
                .append("\"total_entities\":").append(totalEntities).append(",")
                .append("\"list\":[").append(worldsSb).append("]")
                .append("},")
                .append("\"players\":{")
                .append("\"online\":").append(onlineCount).append(",")
                .append("\"max\":").append(maxPlayers).append(",")
                .append("\"list\":[").append(playersSb).append("]")
                .append("}")
                .append("}");

        return sb.toString();
    }

    private long[] getLinuxMemoryMb() {
        try {
            File meminfo = new File("/proc/meminfo");
            if (meminfo.exists() && meminfo.canRead()) {
                long totalKb = -1;
                long availKb = -1;
                try (BufferedReader reader = new BufferedReader(new FileReader(meminfo))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("MemTotal:")) {
                            totalKb = parseMemKb(line);
                        } else if (line.startsWith("MemAvailable:")) {
                            availKb = parseMemKb(line);
                        }
                        if (totalKb > 0 && availKb > 0) break;
                    }
                }
                if (totalKb > 0 && availKb > 0) {
                    return new long[]{ totalKb / 1024, availKb / 1024 };
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private long parseMemKb(String line) {
        try {
            String[] parts = line.split("\\s+");
            if (parts.length >= 2) {
                return Long.parseLong(parts[1]);
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\b", "\\b")
                  .replace("\f", "\\f")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
