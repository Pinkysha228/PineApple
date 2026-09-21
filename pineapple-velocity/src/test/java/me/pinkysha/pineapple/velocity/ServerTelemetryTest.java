package me.pinkysha.pineapple.velocity;

import me.pinkysha.pineapple.velocity.server.SessionManager;
import me.pinkysha.pineapple.velocity.telemetry.ServerTelemetryRegistry;
import me.pinkysha.pineapple.velocity.telemetry.ServerTelemetryState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerTelemetryTest {

    private ServerTelemetryRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ServerTelemetryRegistry();
    }

    @Test
    void testRegisterAndHandleTelemetry() {
        registry.registerServer("vanilla");
        registry.registerServer("building");

        assertEquals(2, registry.getAllServers().size());

        String jsonTelemetry = """
            {
              "server_id": "vanilla",
              "server_name": "vanilla",
              "version": "Paper 1.21.4 (Git-123)",
              "java_version": "21.0.6",
              "uptime": "2h 15m",
              "tps": {"current": 19.8, "mspt": 5.2, "1m": 20.0, "5m": 19.9, "15m": 20.0},
              "memory": {"used_mb": 4096, "committed_mb": 6144, "max_mb": 8192, "percentage": 50, "system_total_mb": 16384},
              "cpu": {"process_percentage": 25.5, "system_percentage": 35.0, "cores": 8},
              "worlds": [{"name": "world", "chunks": 500, "entities": 120}],
              "players": {"online": 3, "max": 100, "list": []}
            }
            """;

        registry.handleIncomingTelemetry("vanilla", jsonTelemetry);

        ServerTelemetryState vanilla = registry.getServer("vanilla").orElseThrow();
        assertTrue(vanilla.isOnline());
        assertEquals("vanilla", vanilla.getServerName());
        assertEquals(19.8, vanilla.getTps(), 0.01);
        assertEquals(4096, vanilla.getRamUsedMb());
        assertEquals(50, vanilla.getRamPct());
        assertEquals(25.5, vanilla.getCpuProcessPct(), 0.01);
        assertEquals(3, vanilla.getOnlinePlayers());

        // Check network overview JSON
        String overview = registry.getNetworkOverviewJson();
        assertTrue(overview.contains("\"active_count\":1"));
        assertTrue(overview.contains("\"total_count\":2"));
        assertTrue(overview.contains("\"vanilla\""));
        assertTrue(overview.contains("\"building\""));

        // Check history JSON
        String history = registry.getServerHistoryJson("vanilla");
        assertTrue(history.contains("\"tps\":19.8"));
        assertTrue(history.contains("\"ram\":4096"));
    }

    @Test
    void testHandleIncomingTelemetryNullOrigin() {
        registry.registerServer("building");
        String jsonTelemetry = """
            {
              "server_id": "building",
              "server_name": "building",
              "version": "Paper 1.21.4",
              "tps": {"current": 20.0},
              "memory": {"used_mb": 2048, "percentage": 25},
              "cpu": {"process_percentage": 10.0},
              "players": {"online": 0, "max": 50}
            }
            """;
        registry.handleIncomingTelemetry(null, jsonTelemetry);

        ServerTelemetryState building = registry.getServer("building").orElseThrow();
        assertTrue(building.isOnline());
        assertEquals("building", building.getServerName());
        assertEquals(20.0, building.getTps(), 0.01);
        assertEquals(2048, building.getRamUsedMb());
    }

    @Test
    void testServerTimeout() throws InterruptedException {
        registry.registerServer("vanilla");
        String jsonTelemetry = "{\"server_id\":\"vanilla\",\"tps\":{\"current\":20.0},\"memory\":{},\"cpu\":{},\"players\":{}}";
        registry.handleIncomingTelemetry("vanilla", jsonTelemetry);

        ServerTelemetryState vanilla = registry.getServer("vanilla").orElseThrow();
        assertTrue(vanilla.isOnline());

        // Check with 0 timeout -> should become offline
        registry.checkServerTimeouts(-1L);
        assertFalse(vanilla.isOnline());
    }

    @Test
    void testSessionManager() {
        SessionManager sessionManager = new SessionManager();
        String token = sessionManager.createSession(1);
        assertNotNull(token);
        assertTrue(sessionManager.isValid(token));

        sessionManager.invalidate(token);
        assertFalse(sessionManager.isValid(token));
    }
}
