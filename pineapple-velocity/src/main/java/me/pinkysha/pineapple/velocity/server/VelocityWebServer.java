package me.pinkysha.pineapple.velocity.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import me.pinkysha.pineapple.velocity.config.VelocityConfig;
import me.pinkysha.pineapple.velocity.telemetry.ServerTelemetryRegistry;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class VelocityWebServer {

    private final VelocityConfig config;
    private final ServerTelemetryRegistry registry;
    private final SessionManager sessionManager;
    private final Path dataDirectory;
    private final Logger logger;
    private HttpServer server;

    private final Map<String, CachedResource> resourceCache = new HashMap<>();

    private record CachedResource(byte[] data, String contentType, String etag, long lastModified) {}

    public VelocityWebServer(VelocityConfig config, ServerTelemetryRegistry registry, SessionManager sessionManager, Logger logger) {
        this(config, registry, sessionManager, null, logger);
    }

    public VelocityWebServer(VelocityConfig config, ServerTelemetryRegistry registry, SessionManager sessionManager, Path dataDirectory, Logger logger) {
        this.config = config;
        this.registry = registry;
        this.sessionManager = sessionManager;
        this.dataDirectory = dataDirectory;
        this.logger = logger;
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
                            logger.info("Extracted default web asset to: {}", target);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Could not extract default web asset {}: {}", rel, e.getMessage());
                }
            }
        }
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(config.host(), config.port()), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/", new RootHandler());
        server.createContext("/login", new LoginHandler());
        server.createContext("/logout", new LogoutHandler());
        server.createContext("/dashboard", new DashboardHandler());
        server.createContext("/api/servers", new ApiServersHandler());
        server.createContext("/api/stats", new ApiStatsHandler());
        server.createContext("/api/history", new ApiHistoryHandler());
        server.createContext("/api/telemetry/push", new ApiTelemetryPushHandler());
        server.createContext("/static/", new StaticHandler());
        server.createContext("/css/", new StaticHandler());
        server.createContext("/js/", new StaticHandler());
        server.createContext("/favicon.ico", new FaviconHandler());

        server.start();
        logger.info("PineApple Velocity Web Server started at http://{}:{}", config.host(), config.port());
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
            logger.info("PineApple Velocity Web Server stopped.");
        }
    }

    private boolean isAuthenticated(HttpExchange exchange) {
        if (!config.isAuthEnabled()) return true;
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) return false;

        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && "pineapple_session".equals(parts[0])) {
                return sessionManager.isValid(parts[1]);
            }
        }
        return false;
    }

    private void sendRedirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
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
                    logger.warn("Failed to read external web asset {}: {}", externalFile, e.getMessage());
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

        String ifNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");
        if (ifNoneMatch != null && ifNoneMatch.equals(res.etag())) {
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
        exchange.sendResponseHeaders(200, res.data().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(res.data());
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isBlank()) return map;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            map.put(key, value);
        }
        return map;
    }

    private Map<String, String> parseRequestBody(String body, String contentType) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isBlank()) return map;

        if (contentType != null && contentType.contains("application/json")) {
            try {
                JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
                for (String key : obj.keySet()) {
                    if (obj.get(key).isJsonPrimitive()) {
                        map.put(key, obj.get(key).getAsString());
                    }
                }
                return map;
            } catch (Exception ignored) {}
        }

        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            try {
                String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                String v = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
                map.put(k, v);
            } catch (Exception ignored) {}
        }
        return map;
    }

    private class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (!path.equals("/")) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            if (isAuthenticated(exchange)) {
                sendRedirect(exchange, "/dashboard");
            } else {
                serveStatic(exchange, "/web/index.html", "text/html; charset=utf-8");
            }
        }
    }

    private class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthenticated(exchange)) {
                sendRedirect(exchange, "/");
                return;
            }
            serveStatic(exchange, "/web/dashboard.html", "text/html; charset=utf-8");
        }
    }

    private class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                if (isAuthenticated(exchange)) {
                    sendRedirect(exchange, "/dashboard");
                    return;
                }
                serveStatic(exchange, "/web/index.html", "text/html; charset=utf-8");
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                Map<String, String> params = parseRequestBody(body, contentType);

                String user = params.getOrDefault("login", params.getOrDefault("username", "")).trim();
                String pass = params.getOrDefault("password", "").trim();
                boolean remember = "true".equalsIgnoreCase(params.getOrDefault("remember_me", "true"))
                        || "on".equalsIgnoreCase(params.getOrDefault("remember_me", ""));

                boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(exchange.getRequestHeaders().getFirst("X-Requested-With"))
                        || (contentType != null && contentType.contains("application/json"));

                if (config.getUsername().equals(user) && config.getPassword().equals(pass)) {
                    String cookieHeader;
                    if (remember) {
                        int days = Math.max(1, config.getRememberMeDays());
                        String token = sessionManager.createSession(days);
                        long maxAgeSec = (long) days * 86400L;
                        Instant expiryInstant = Instant.now().plus(Duration.ofDays(days));
                        String expiresFormatted = DateTimeFormatter.RFC_1123_DATE_TIME.format(expiryInstant.atZone(ZoneOffset.UTC));
                        cookieHeader = "pineapple_session=" + token + "; Path=/; Max-Age=" + maxAgeSec + "; Expires=" + expiresFormatted + "; HttpOnly; SameSite=Lax";
                    } else {
                        String token = sessionManager.createSession(1);
                        cookieHeader = "pineapple_session=" + token + "; Path=/; HttpOnly; SameSite=Lax";
                    }
                    exchange.getResponseHeaders().set("Set-Cookie", cookieHeader);

                    if (isAjax) {
                        sendJson(exchange, 200, "{\"success\":true,\"message\":\"Authenticated successfully!\",\"redirect\":\"/dashboard\"}");
                    } else {
                        sendRedirect(exchange, "/dashboard");
                    }
                } else {
                    if (isAjax) {
                        sendJson(exchange, 401, "{\"success\":false,\"message\":\"Invalid username or password\"}");
                    } else {
                        sendRedirect(exchange, "/?error=invalid_credentials");
                    }
                }
            }
        }
    }

    private class LogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookieHeader != null) {
                for (String cookie : cookieHeader.split(";")) {
                    String[] parts = cookie.trim().split("=", 2);
                    if (parts.length == 2 && "pineapple_session".equals(parts[0])) {
                        sessionManager.invalidate(parts[1]);
                    }
                }
            }
            exchange.getResponseHeaders().set("Set-Cookie", "pineapple_session=; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; HttpOnly; SameSite=Lax");
            sendRedirect(exchange, "/");
        }
    }

    private class ApiServersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthenticated(exchange)) {
                sendJson(exchange, 401, "{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                return;
            }
            sendJson(exchange, 200, registry.getNetworkOverviewJson());
        }
    }

    private class ApiStatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthenticated(exchange)) {
                sendJson(exchange, 401, "{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                return;
            }
            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String serverId = params.get("server");
            sendJson(exchange, 200, registry.getServerStatsJson(serverId));
        }
    }

    private class ApiHistoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthenticated(exchange)) {
                sendJson(exchange, 401, "{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                return;
            }
            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String serverId = params.get("server");
            sendJson(exchange, 200, registry.getServerHistoryJson(serverId));
        }
    }

    private class ApiTelemetryPushHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            try (InputStream in = exchange.getRequestBody()) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                if (json.isBlank()) {
                    sendJson(exchange, 400, "{\"error\":\"Empty payload\"}");
                    return;
                }

                String targetServer = "unknown";
                try {
                    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                    if (root.has("server_id") && !root.get("server_id").getAsString().isBlank()) {
                        targetServer = root.get("server_id").getAsString();
                    } else if (root.has("server_name") && !root.get("server_name").getAsString().isBlank()) {
                        targetServer = root.get("server_name").getAsString();
                    }
                } catch (Exception ignored) {}

                boolean isNew = registry.handleIncomingTelemetry(null, json);
                if (isNew) {
                    logger.info("Server '{}' connected via HTTP telemetry stream.", targetServer);
                }

                sendJson(exchange, 200, "{\"status\":\"ok\"}");
            } catch (Exception e) {
                logger.warn("Error handling HTTP telemetry push: {}", e.getMessage());
                sendJson(exchange, 500, "{\"error\":\"Internal error\"}");
            }
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

    private class FaviconHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            CachedResource res = loadResource("/web/favicon.ico", "image/x-icon");
            if (res != null) {
                serveStatic(exchange, "/web/favicon.ico", "image/x-icon");
            } else {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
            }
        }
    }
}
