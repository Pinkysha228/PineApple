package me.pinkysha.pineapple.velocity;

import me.pinkysha.pineapple.velocity.config.VelocityConfig;
import me.pinkysha.pineapple.velocity.server.HttpSecurity;
import me.pinkysha.pineapple.velocity.server.LoginRateLimiter;
import me.pinkysha.pineapple.velocity.server.SessionManager;
import me.pinkysha.pineapple.velocity.server.VelocityWebServer;
import me.pinkysha.pineapple.velocity.telemetry.ServerTelemetryRegistry;
import me.pinkysha.pineapple.velocity.user.UserManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WebSecurityTest {

    private static final Logger LOG = LoggerFactory.getLogger("PineAppleTest");

    @TempDir
    Path dataDir;

    private VelocityWebServer server;
    private SessionManager sessions;
    private UserManager users;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        Files.writeString(dataDir.resolve("config.properties"),
                "server.host=127.0.0.1\nserver.port=" + port + "\nauth.password=correct-horse-battery\n");
        VelocityConfig config = new VelocityConfig(dataDir, LOG);
        sessions = new SessionManager(dataDir);
        users = new UserManager(dataDir, config, LOG);
        users.setCredentialsRevokedListener(sessions::invalidateUser);
        sessions.createSession("admin", 1);
        server = new VelocityWebServer(config, new ServerTelemetryRegistry(), sessions, users, dataDir, LOG);
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop();
    }

    /** Sends a raw request so paths like "/static/../x" reach the server un-normalized. */
    private String rawRequest(String request) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();
            InputStream in = socket.getInputStream();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String get(String path) throws IOException {
        return rawRequest("GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\nConnection: close\r\n\r\n");
    }

    private static int status(String response) {
        return Integer.parseInt(response.substring(9, 12));
    }

    @Test
    void secretFilesInDataFolderAreNeverServed() throws IOException {
        assertTrue(Files.exists(dataDir.resolve("sessions.json")));
        assertTrue(Files.exists(dataDir.resolve("users.json")));
        for (String path : new String[]{
                "/static/sessions.json", "/static/users.json", "/static/config.properties",
                "/css/../sessions.json", "/static/../sessions.json", "/static/web/../users.json",
                "/js/..%2fusers.json", "/lang/../users.json", "/lang/..%2fusers.json"}) {
            String resp = get(path);
            assertNotEquals(200, status(resp), "must not serve " + path);
            assertFalse(resp.contains("password_hash"), "leaked users.json via " + path);
            assertFalse(resp.contains("expiry"), "leaked sessions.json via " + path);
        }
    }

    @Test
    void regularStaticAssetsStillWork() throws IOException {
        assertEquals(200, status(get("/static/css/base.css")));
        assertEquals(200, status(get("/js/utils.js")));
        assertEquals(200, status(get("/lang/en.json")));
    }

    @Test
    void loginIsLockedOutAfterRepeatedFailures() throws IOException {
        String body = "{\"login\":\"admin\",\"password\":\"wrong\"}";
        String req = "POST /login HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\nContent-Type: application/json\r\n"
                + "Content-Length: " + body.length() + "\r\nConnection: close\r\n\r\n" + body;
        for (int i = 0; i < 5; i++) {
            assertEquals(401, status(rawRequest(req)));
        }
        assertEquals(429, status(rawRequest(req)));
    }

    @Test
    void crossOriginLoginIsRejected() throws IOException {
        String body = "login=admin&password=correct-horse-battery";
        String resp = rawRequest("POST /login HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\nOrigin: https://evil.example\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: " + body.length()
                + "\r\nConnection: close\r\n\r\n" + body);
        assertEquals(403, status(resp));
    }

    @Test
    void passwordChangeRevokesExistingSessions() {
        String token = sessions.createSession("admin", 1);
        assertTrue(sessions.isValid(token));
        assertTrue(users.updatePassword("admin", "another-long-password"));
        assertFalse(sessions.isValid(token));
    }

    @Test
    void pathHelpersRejectTraversal() {
        assertTrue(HttpSecurity.isSafeRelativePath("css/base.css"));
        assertFalse(HttpSecurity.isSafeRelativePath("../users.json"));
        assertFalse(HttpSecurity.isSafeRelativePath("css/../../x"));
        assertFalse(HttpSecurity.isSafeRelativePath("/etc/passwd"));
        assertFalse(HttpSecurity.isSafeRelativePath("css\\..\\x"));
        assertNull(HttpSecurity.resolveInside(dataDir.resolve("web"), "../users.json"));
        assertTrue(HttpSecurity.isValidLangCode("en"));
        assertFalse(HttpSecurity.isValidLangCode("../users"));
    }

    @Test
    void rateLimiterResetsOnSuccess() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int i = 0; i < 4; i++) limiter.recordFailure("1.2.3.4");
        assertEquals(0, limiter.retryAfterMs("1.2.3.4"));
        limiter.recordSuccess("1.2.3.4");
        for (int i = 0; i < 4; i++) limiter.recordFailure("1.2.3.4");
        assertEquals(0, limiter.retryAfterMs("1.2.3.4"));
        limiter.recordFailure("1.2.3.4");
        assertTrue(limiter.retryAfterMs("1.2.3.4") > 0);
    }
}
