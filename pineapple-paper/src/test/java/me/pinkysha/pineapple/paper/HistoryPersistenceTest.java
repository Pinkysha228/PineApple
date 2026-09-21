package me.pinkysha.pineapple.paper;

import me.pinkysha.pineapple.paper.config.MonitorConfig;
import me.pinkysha.pineapple.paper.metrics.StatsCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HistoryPersistenceTest {

    @TempDir
    Path tempDir;

    private StatsCollector statsCollector;

    static class DummyConfig implements MonitorConfig {
        @Override public String serverName() { return "vanilla"; }
        @Override public String channel() { return "pineapple:telemetry"; }
        @Override public boolean isStandaloneWebServer() { return false; }
        @Override public boolean isAuthEnabled() { return false; }
        @Override public String getUsername() { return "admin"; }
        @Override public String getPassword() { return "admin"; }
        @Override public int getRememberMeDays() { return 30; }
        @Override public int getMaxPlayersDisplay() { return 10; }
        @Override public long getCacheTtlMs() { return 1000L; }
        @Override public void logInfo(String message) {}
        @Override public void logWarning(String message) {}
        @Override public void logSevere(String message) {}
    }

    @BeforeEach
    void setUp() {
        statsCollector = new StatsCollector(new DummyConfig());
    }

    @Test
    void testRecordHistoryPointAndBuffer() {
        assertEquals(0, statsCollector.getHistoryBufferSize());

        statsCollector.recordHistoryPoint();
        assertEquals(1, statsCollector.getHistoryBufferSize());

        String json = statsCollector.getHistoryJson();
        assertTrue(json.startsWith("[{"));
        assertTrue(json.contains("\"t\":"));
        assertTrue(json.contains("\"tps\":"));
        assertTrue(json.contains("\"ram\":"));
        assertTrue(json.contains("\"cpu\":"));
        assertTrue(json.endsWith("}]"));
    }

    @Test
    void testSaveAndLoadHistoryPersistence() {
        long baseTime = 1700000000000L;
        statsCollector.addHistoryPoint(new StatsCollector.HistoryPoint(baseTime, 20.0, 5.0, 512, 2048, 25, 10.0));
        statsCollector.addHistoryPoint(new StatsCollector.HistoryPoint(baseTime + 2500, 19.8, 6.2, 530, 2048, 26, 12.5));
        statsCollector.addHistoryPoint(new StatsCollector.HistoryPoint(baseTime + 5000, 20.0, 4.8, 540, 2048, 26, 8.0));

        assertEquals(3, statsCollector.getHistoryBufferSize());

        File historyFile = tempDir.resolve("history.json").toFile();
        statsCollector.saveHistoryToFile(historyFile);

        assertTrue(historyFile.exists());
        assertTrue(historyFile.length() > 0);

        StatsCollector freshCollector = new StatsCollector(new DummyConfig());
        assertEquals(0, freshCollector.getHistoryBufferSize());

        freshCollector.loadHistoryFromFile(historyFile);
        assertEquals(3, freshCollector.getHistoryBufferSize());

        String json = freshCollector.getHistoryJson();
        assertTrue(json.contains("1700000000000"));
        assertTrue(json.contains("1700000002500"));
        assertTrue(json.contains("1700000005000"));
    }

    @Test
    void testLoadFromNonExistentOrCorruptedFile() throws Exception {
        File nonExistent = tempDir.resolve("does_not_exist.json").toFile();
        assertDoesNotThrow(() -> statsCollector.loadHistoryFromFile(nonExistent));
        assertEquals(0, statsCollector.getHistoryBufferSize());

        File corrupted = tempDir.resolve("corrupted.json").toFile();
        Files.writeString(corrupted.toPath(), "{ not an array }");
        assertDoesNotThrow(() -> statsCollector.loadHistoryFromFile(corrupted));
        assertEquals(0, statsCollector.getHistoryBufferSize());
    }

    @Test
    void testHistoryBufferMaxCapacity() {
        long baseTime = 1700000000000L;
        for (int i = 0; i < StatsCollector.MAX_HISTORY_POINTS + 100; i++) {
            statsCollector.addHistoryPoint(new StatsCollector.HistoryPoint(baseTime + i * 2500L, 20.0, 5.0, 500, 2048, 24, 5.0));
        }

        assertEquals(StatsCollector.MAX_HISTORY_POINTS, statsCollector.getHistoryBufferSize());
    }

    @Test
    void testBuildStatsJsonContainsServerId() {
        String json = statsCollector.buildStatsJson();
        assertTrue(json.contains("\"server_id\":\"vanilla\""));
        assertTrue(json.contains("\"server_name\":\"vanilla\""));
        assertTrue(json.contains("\"tps\":"));
        assertTrue(json.contains("\"memory\":"));
        assertTrue(json.contains("\"cpu\":"));
    }
}
