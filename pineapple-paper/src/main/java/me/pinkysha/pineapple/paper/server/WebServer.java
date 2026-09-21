package me.pinkysha.pineapple.paper.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import me.pinkysha.pineapple.paper.config.MonitorConfig;
import me.pinkysha.pineapple.paper.metrics.StatsCollector;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Embedded HTTP server for Paper standalone fallback.
 */
public class WebServer {

    private final MonitorConfig config;
    private final String host;
    private final int port;
    private final StatsCollector statsCollector;
    private final SessionManager sessionManager;
    private final Path dataDirectory;

    private HttpServer server;
    private ThreadPoolExecutor executor;

    private final Map<String, CachedResource> resourceCache = new HashMap<>();

    private record CachedResource(byte[] data, String contentType, String etag, long lastModified) {}

    private static final String COOKIE_NAME = "pmonitor_session";

    public WebServer(MonitorConfig config, String host, int port, StatsCollector statsCollector, SessionManager sessionManager) {
        this(config, host, port, statsCollector, sessionManager, null);
    }

    public WebServer(MonitorConfig config, String host, int port, StatsCollector statsCollector, SessionManager sessionManager, Path dataDirectory) {
        this.config = config;
        this.host = host;
        this.port = port;
        this.statsCollector = statsCollector;
        this.sessionManager = sessionManager;
        this.dataDirectory = dataDirectory;
        extractDefaultWebAssets();
    }

    private void extractDefaultWebAssets() {
        if (dataDirectory == null) return;
        Path webDir = dataDirectory.resolve("web");
        String[] defaultFiles = {
            "index.html",
            "dashboard.html",
            "css/style.css",
            "js/main.js"
        };
        for (String rel : defaultFiles) {
            Path target = webDir.resolve(rel);
            if (!Files.exists(target)) {
                try {
                    if (target.getParent() != null) {
                        Files.createDirectories(target.getParent());
                    }
                    try (InputStream in = getClass().getResourceAsStream("/web/" + rel)) {
                        if (in != null) {
                            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                            config.logInfo("Extracted default web asset to: " + target);
                        }
                    }
                } catch (Exception e) {
                    config.logWarning("Could not extract default web asset " + rel + ": " + e.getMessage());
                }
            }
        }
    }

    private synchronized CachedResource loadResource(String path, String contentType) {
        // 1. Check external file in dataDirectory/web/
        if (dataDirectory != null) {
            String rel = path.startsWith("/web/") ? path.substring("/web/".length())
                    : (path.startsWith("/") ? path.substring(1) : path);
            Path externalFile = dataDirectory.resolve("web").resolve(rel);
            if (Files.isRegularFile(externalFile)) {
                try {
                    long fileLastMod = Files.getLastModifiedTime(externalFile).toMillis();
                    CachedResource cached = resourceCache.get(path);
                    if (cached != null && cached.lastModified() == fileLastMod) {
                        return cached;
                    }
                    byte[] data = Files.readAllBytes(externalFile);
                    String etag = computeEtag(data);
                    CachedResource fresh = new CachedResource(data, contentType, etag, fileLastMod);
                    resourceCache.put(path, fresh);
                    return fresh;
                } catch (IOException e) {
                    config.logWarning("Failed to read external web asset " + externalFile + ": " + e.getMessage());
                }
            }
        }

        // 2. Fallback to classpath resource inside JAR
        CachedResource cached = resourceCache.get(path);
        if (cached != null && cached.lastModified() == 0L) {
            return cached;
        }

        InputStream is = getClass().getResourceAsStream(path);
        if (is == null && path.startsWith("/")) {
            is = getClass().getClassLoader().getResourceAsStream(path.substring(1));
        }
        if (is == null) {
            return null;
        }
        try (InputStream in = is) {
            byte[] data = in.readAllBytes();
            String etag = computeEtag(data);
            CachedResource res = new CachedResource(data, contentType, etag, 0L);
            resourceCache.put(path, res);
            return res;
        } catch (IOException e) {
            return null;
        }
    }

    private String computeEtag(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder("\"");
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            sb.append('"');
            return sb.toString();
        } catch (Exception e) {
            return "\"" + Integer.toHexString(data.length) + "\"";
        }
    }

    private void serveStatic(HttpExchange exchange, String resourcePath, String contentType) throws IOException {
        CachedResource res = loadResource(resourcePath, contentType);
        if (res == null) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }

        String clientEtag = exchange.getRequestHeaders().getFirst("If-None-Match");
        if (clientEtag != null && clientEtag.equals(res.etag())) {
            exchange.sendResponseHeaders(304, -1);
            exchange.close();
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", res.contentType());
        exchange.getResponseHeaders().set("ETag", res.etag());
        if (res.contentType().contains("text/html")) {
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
        } else {
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, must-revalidate");
        }
        sendResponse(exchange, 200, res.contentType(), res.data());
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r, "PineApple-WebWorker");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);

        server.createContext("/", new RootHandler());
        server.createContext("/login", new LoginHandler());
        server.createContext("/logout", new LogoutHandler());
        server.createContext("/dashboard", new DashboardHandler());
        server.createContext("/api/stats", new StatsApiHandler());
        server.createContext("/api/history", new HistoryApiHandler());
        server.createContext("/api/servers", new ServersApiHandler());
        server.createContext("/static/", new StaticHandler());
        server.createContext("/css/", new StaticHandler());
        server.createContext("/js/", new StaticHandler());

        server.start();
        config.logInfo("PineApple Standalone Web Monitor started at http://" + (host.equals("0.0.0.0") ? "127.0.0.1" : host) + ":" + port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private boolean isAuthEnabled() {
        return config != null && config.isAuthEnabled();
    }

    private boolean isAuthenticated(HttpExchange exchange) {
        if (!isAuthEnabled()) {
            return true;
        }
        String token = getCookie(exchange, COOKIE_NAME);
        return sessionManager.isValid(token);
    }

    private String getCookie(HttpExchange exchange, String name) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) return null;

        String[] pairs = cookieHeader.split(";");
        for (String pair : pairs) {
            String[] kv = pair.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equalsIgnoreCase(name)) {
                return kv[1].trim();
            }
        }
        return null;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String contentType, byte[] data) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private void sendRedirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
    }

    private class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (!path.equals("/")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            if (isAuthenticated(exchange)) {
                sendRedirect(exchange, "/dashboard");
                return;
            }

            serveStatic(exchange, "/web/index.html", "text/html; charset=UTF-8");
        }
    }

    private class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthenticated(exchange)) {
                sendRedirect(exchange, "/");
                return;
            }

            serveStatic(exchange, "/web/dashboard.html", "text/html; charset=UTF-8");
        }
    }

    private class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendRedirect(exchange, "/");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = parseRequestBody(body, exchange.getRequestHeaders().getFirst("Content-Type"));

            String usernameInput = params.getOrDefault("login", params.getOrDefault("username", "")).trim();
            String passwordInput = params.getOrDefault("password", "").trim();
            boolean rememberMe = "true".equalsIgnoreCase(params.getOrDefault("remember_me", "true"))
                    || "on".equalsIgnoreCase(params.getOrDefault("remember_me", ""));

            String expectedUser = config != null ? config.getUsername() : "admin";
            String expectedPass = config != null ? config.getPassword() : "admin";
            int rememberDays = config != null ? config.getRememberMeDays() : 30;

            if (expectedUser.equals(usernameInput) && expectedPass.equals(passwordInput)) {
                String token = sessionManager.createSession(usernameInput, rememberMe, rememberDays);

                StringBuilder cookieHeader = new StringBuilder();
                cookieHeader.append(COOKIE_NAME).append("=").append(token).append("; Path=/; HttpOnly; SameSite=Lax");
                if (rememberMe) {
                    int days = Math.max(1, rememberDays);
                    long maxAgeSec = (long) days * 24L * 3600L;
                    Instant expiryInstant = Instant.now().plus(Duration.ofDays(days));
                    String expiresFormatted = DateTimeFormatter.RFC_1123_DATE_TIME.format(expiryInstant.atZone(ZoneOffset.UTC));
                    cookieHeader.append("; Max-Age=").append(maxAgeSec).append("; Expires=").append(expiresFormatted);
                }
                exchange.getResponseHeaders().set("Set-Cookie", cookieHeader.toString());

                String resp = "{\"success\":true,\"message\":\"Authenticated successfully!\",\"redirect\":\"/dashboard\"}";
                sendResponse(exchange, 200, "application/json; charset=UTF-8", resp.getBytes(StandardCharsets.UTF_8));
            } else {
                String resp = "{\"success\":false,\"message\":\"Invalid credentials. Check config.yml\"}";
                sendResponse(exchange, 401, "application/json; charset=UTF-8", resp.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private class LogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String token = getCookie(exchange, COOKIE_NAME);
            if (token != null) {
                sessionManager.invalidate(token);
            }

            exchange.getResponseHeaders().add("Set-Cookie", COOKIE_NAME + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
            sendRedirect(exchange, "/");
        }
    }

    private class ServersApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthenticated(exchange)) {
                String error = "{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}";
                sendResponse(exchange, 401, "application/json; charset=UTF-8", error.getBytes(StandardCharsets.UTF_8));
                return;
            }

            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            String sName = config != null ? config.serverName() : "vanilla";
            String json = "{\"network\":{\"total_servers\":1,\"online_servers\":1,\"total_players\":0},\"servers\":[{\"id\":\""
                    + sName + "\",\"name\":\"" + sName + "\",\"online\":true,\"version\":\"Paper 1.21.4\",\"tps\":20.0,\"ram_used_mb\":0,\"ram_max_mb\":1024,\"ram_pct\":0,\"cpu_pct\":0.0,\"online_players\":0,\"max_players\":20,\"last_seen\":"
                    + System.currentTimeMillis() + "}]}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            sendResponse(exchange, 200, "application/json; charset=UTF-8", bytes);
        }
    }

    private class HistoryApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthenticated(exchange)) {
                String error = "{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}";
                sendResponse(exchange, 401, "application/json; charset=UTF-8", error.getBytes(StandardCharsets.UTF_8));
                return;
            }

            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            String json = statsCollector.getHistoryJson();
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            sendResponse(exchange, 200, "application/json; charset=UTF-8", bytes);
        }
    }

    private class StatsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthenticated(exchange)) {
                String error = "{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}";
                sendResponse(exchange, 401, "application/json; charset=UTF-8", error.getBytes(StandardCharsets.UTF_8));
                return;
            }

            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            String json = statsCollector.getStatsJson();
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            sendResponse(exchange, 200, "application/json; charset=UTF-8", bytes);
        }
    }

    private class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/static/")) {
                path = path.substring(7);
            }

            String contentType = "text/plain";
            if (path.endsWith(".css")) contentType = "text/css; charset=utf-8";
            else if (path.endsWith(".js")) contentType = "application/javascript; charset=utf-8";
            else if (path.endsWith(".svg")) contentType = "image/svg+xml";
            else if (path.endsWith(".png")) contentType = "image/png";
            else if (path.endsWith(".woff2")) contentType = "font/woff2";
            else if (path.endsWith(".woff")) contentType = "font/woff";
            else if (path.endsWith(".ttf")) contentType = "font/ttf";
            else if (path.endsWith(".ico")) contentType = "image/x-icon";

            serveStatic(exchange, "/web" + (path.startsWith("/") ? path : "/" + path), contentType);
        }
    }

    private Map<String, String> parseRequestBody(String body, String contentType) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isBlank()) return map;

        String[] pairs = body.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            try {
                String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                String v = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
                map.put(k, v);
            } catch (Exception ignored) {}
        }
        return map;
    }
}
