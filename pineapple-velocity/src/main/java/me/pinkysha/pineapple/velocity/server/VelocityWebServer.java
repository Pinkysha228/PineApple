package me.pinkysha.pineapple.velocity.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import me.pinkysha.pineapple.velocity.config.VelocityConfig;
import me.pinkysha.pineapple.velocity.security.PasswordSecurity;
import me.pinkysha.pineapple.velocity.telemetry.ServerTelemetryRegistry;
import me.pinkysha.pineapple.velocity.telemetry.ServerTelemetryState;
import me.pinkysha.pineapple.velocity.user.UserManager;
import org.slf4j.Logger;

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
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class VelocityWebServer {

    private final VelocityConfig config;
    private final ServerTelemetryRegistry registry;
    private final SessionManager sessionManager;
    private final UserManager userManager;
    private final Path dataDirectory;
    private final Logger logger;

    private HttpServer server;
    private final Map<String, CachedResource> resourceCache = new ConcurrentHashMap<>();
    private final LoginRateLimiter loginRateLimiter = new LoginRateLimiter();

    private record CachedResource(byte[] data, String contentType, String etag, long lastModified) {}

        private static final String[] DEFAULT_LANGUAGES = {
            "en", "ru", "zh", "fr", "es", "ar"
    };

    private static final Map<String, String[]> LANG_META = new LinkedHashMap<>();
    static {
        LANG_META.put("en", new String[]{"English", "English", "gb"});
        LANG_META.put("ru", new String[]{"Russian", "Русский", "ru"});
        LANG_META.put("zh", new String[]{"Chinese", "简体中文", "cn"});
        LANG_META.put("fr", new String[]{"French", "Français", "fr"});
        LANG_META.put("es", new String[]{"Spanish", "Español", "es"});
        LANG_META.put("ar", new String[]{"Arabic", "العربية", "sa"});
    }

    public VelocityWebServer(VelocityConfig config, ServerTelemetryRegistry registry, SessionManager sessionManager, Path dataDirectory, Logger logger) {
        this(config, registry, sessionManager, new UserManager(dataDirectory != null ? dataDirectory : Path.of("."), config, logger), dataDirectory, logger);
    }

    public VelocityWebServer(VelocityConfig config, ServerTelemetryRegistry registry, SessionManager sessionManager, UserManager userManager, Path dataDirectory, Logger logger) {
        this.config = config;
        this.registry = registry;
        this.sessionManager = sessionManager;
        this.userManager = userManager;
        this.dataDirectory = dataDirectory;
        this.logger = logger;
        extractDefaultWebAssets();
        extractDefaultLangAssets();
    }

    private void extractDefaultWebAssets() {
        if (dataDirectory == null) return;
        Path webDir = dataDirectory.resolve("web");
        String[] defaultFiles = {
                "index.html",
                "dashboard.html",
                "css/style.css",
                "css/base.css",
                "css/auth.css",
                "css/components.css",
                "css/dashboard.css",
                "css/theme.css",
                "css/profile.css",
                "css/console.css",
                "js/main.js",
                "js/i18n.js",
                "js/utils.js",
                "js/auth.js",
                "js/profile.js",
                "js/console.js",
                "js/dashboard.js"
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

    private void extractDefaultLangAssets() {
        if (dataDirectory == null) return;
        Path langDir = dataDirectory.resolve("lang");
        for (String code : DEFAULT_LANGUAGES) {
            String fileName = code + ".json";
            Path target = langDir.resolve(fileName);
            if (!Files.exists(target)) {
                try {
                    if (target.getParent() != null) {
                        Files.createDirectories(target.getParent());
                    }
                    try (InputStream in = getClass().getResourceAsStream("/lang/" + fileName)) {
                        if (in != null) {
                            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                            logger.info("Extracted default lang asset to: {}", target);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Could not extract default lang asset {}: {}", fileName, e.getMessage());
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
        server.createContext("/profile", new ProfileHandler());

        server.createContext("/api/me", new ApiMeHandler());
        server.createContext("/api/servers", new ApiServersHandler());
        server.createContext("/api/stats", new ApiStatsHandler());
        server.createContext("/api/history", new ApiHistoryHandler());
        server.createContext("/api/telemetry/push", new ApiTelemetryPushHandler());
        server.createContext("/api/console/logs", new ApiConsoleLogsHandler());
        server.createContext("/api/console/command", new ApiConsoleCommandHandler());
        server.createContext("/api/password", new ApiPasswordHandler());
        server.createContext("/api/lang", new ApiLangHandler());
        server.createContext("/lang/", new StaticLangHandler());
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
        if (!config.isAuthEnabled()) {
            return true;
        }
        String token = getSessionToken(exchange);
        return token != null && sessionManager.isValid(token);
    }

    /**
     * Verifies that an incoming telemetry push carries the shared secret via
     * "Authorization: Bearer &lt;secret&gt;". Uses a constant-time comparison.
     * Returns false if no secret is configured, so unauthenticated pushes are rejected.
     */
    private boolean isTrustedTelemetrySource(HttpExchange exchange) {
        String expected = config.getTelemetrySecret();
        if (expected == null || expected.isBlank()) {
            return false;
        }
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return false;
        }
        String presented = header.substring(7).trim();
        byte[] a = presented.getBytes(StandardCharsets.UTF_8);
        byte[] b = expected.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    private String getSessionToken(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) return null;
        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && "pineapple_session".equals(parts[0])) {
                return parts[1];
            }
        }
        return null;
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
        // Only files under the "web" folder may ever be served; never the data folder
        // root, which holds config.properties, users.json and sessions.json.
        if (!path.startsWith("/web/")) {
            return null;
        }
        String rel = path.substring("/web/".length());
        if (!HttpSecurity.isSafeRelativePath(rel)) {
            return null;
        }

        if (dataDirectory != null) {
            Path externalFile = HttpSecurity.resolveInside(dataDirectory.resolve("web"), rel);
            if (externalFile != null && Files.isRegularFile(externalFile)) {
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

        CachedResource cached = resourceCache.get(path);
        if (cached != null && cached.lastModified() == 0L) {
            return cached;
        }

        InputStream is = getClass().getResourceAsStream(path);
        if (is == null) {
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

    /**
     * Reads a size-limited request body; on overflow replies 413 and returns null.
     */
    private String readBodyOrReject(HttpExchange exchange) throws IOException {
        try {
            return HttpSecurity.readBody(exchange);
        } catch (HttpSecurity.BodyTooLargeException e) {
            sendJson(exchange, 413, "{\"success\":false,\"message\":\"Request body too large\"}");
            return null;
        }
    }

    /**
     * Blocks cross-site form posts (CSRF) to state-changing endpoints; replies 403 when rejected.
     */
    private boolean rejectCrossOrigin(HttpExchange exchange) throws IOException {
        if (HttpSecurity.isSameOrigin(exchange)) {
            return false;
        }
        logger.warn("Rejected cross-origin {} {} from {}", exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(), HttpSecurity.clientAddress(exchange));
        sendJson(exchange, 403, "{\"success\":false,\"message\":\"Cross-origin request rejected\"}");
        return true;
    }

    /**
     * Console and account endpoints need a real identity, so they are unavailable while auth is disabled.
     */
    private boolean rejectWhenAuthDisabled(HttpExchange exchange) throws IOException {
        if (config.isAuthEnabled()) {
            return false;
        }
        sendJson(exchange, 403, "{\"success\":false,\"message\":\"This feature requires auth.enabled=true\"}");
        return true;
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

    private class ProfileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthenticated(exchange)) {
                sendRedirect(exchange, "/");
                return;
            }
            serveStatic(exchange, "/web/index.html", "text/html; charset=utf-8");
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
                if (rejectCrossOrigin(exchange)) {
                    return;
                }
                String client = HttpSecurity.clientAddress(exchange);
                long retryAfterMs = loginRateLimiter.retryAfterMs(client);
                if (retryAfterMs > 0) {
                    long retryAfterSec = Math.max(1, (retryAfterMs + 999) / 1000);
                    exchange.getResponseHeaders().set("Retry-After", String.valueOf(retryAfterSec));
                    sendJson(exchange, 429, "{\"success\":false,\"message\":\"Too many failed login attempts. Try again in "
                            + retryAfterSec + " seconds.\"}");
                    return;
                }

                String body = readBodyOrReject(exchange);
                if (body == null) {
                    return;
                }
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                Map<String, String> params = parseRequestBody(body, contentType);

                String user = params.getOrDefault("login", params.getOrDefault("username", "")).trim();
                String pass = params.getOrDefault("password", "");
                boolean remember = "true".equalsIgnoreCase(params.getOrDefault("remember_me", "true"))
                        || "on".equalsIgnoreCase(params.getOrDefault("remember_me", ""));

                boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(exchange.getRequestHeaders().getFirst("X-Requested-With"))
                        || (contentType != null && contentType.contains("application/json"));

                if (userManager.verifyPassword(user, pass)) {
                    loginRateLimiter.recordSuccess(client);
                    String secure = HttpSecurity.secureCookieSuffix(exchange);
                    String cookieHeader;
                    if (remember) {
                        int days = Math.max(1, config.getRememberMeDays());
                        String token = sessionManager.createSession(user, days);
                        long maxAgeSec = (long) days * 86400L;
                        Instant expiryInstant = Instant.now().plus(Duration.ofDays(days));
                        String expiresFormatted = DateTimeFormatter.RFC_1123_DATE_TIME.format(expiryInstant.atZone(ZoneOffset.UTC));
                        cookieHeader = "pineapple_session=" + token + "; Path=/; Max-Age=" + maxAgeSec + "; Expires=" + expiresFormatted + "; HttpOnly; SameSite=Lax" + secure;
                    } else {
                        String token = sessionManager.createSession(user, 1);
                        cookieHeader = "pineapple_session=" + token + "; Path=/; HttpOnly; SameSite=Lax" + secure;
                    }
                    exchange.getResponseHeaders().set("Set-Cookie", cookieHeader);

                    if (isAjax) {
                        sendJson(exchange, 200, "{\"success\":true,\"message\":\"Authenticated successfully!\",\"redirect\":\"/dashboard\"}");
                    } else {
                        sendRedirect(exchange, "/dashboard");
                    }
                } else {
                    loginRateLimiter.recordFailure(client);
                    logger.warn("Failed web login for user '{}' from {}", user, client);
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
            String token = getSessionToken(exchange);
            if (token != null) {
                sessionManager.invalidate(token);
            }
            exchange.getResponseHeaders().set("Set-Cookie", "pineapple_session=; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; HttpOnly; SameSite=Lax"
                    + HttpSecurity.secureCookieSuffix(exchange));
            sendRedirect(exchange, "/");
        }
    }

    private class ApiMeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthenticated(exchange)) {
                sendJson(exchange, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }
            String username;
            boolean isSuper;
            if (config.isAuthEnabled()) {
                username = sessionManager.getUsername(getSessionToken(exchange));
                if (username == null) {
                    sendJson(exchange, 401, "{\"error\":\"Unauthorized\"}");
                    return;
                }
                isSuper = userManager.isSuperAdmin(username);
            } else {
                // Without auth there is no identity, so nobody is treated as a superadmin.
                username = "anonymous";
                isSuper = false;
            }

            JsonObject root = new JsonObject();
            root.addProperty("username", username);
            root.addProperty("is_superadmin", isSuper);

            JsonObject perms = new JsonObject();
            for (ServerTelemetryState st : registry.getAllServers()) {
                perms.add(st.getServerId(), st.getUserPermissions(username, isSuper));
            }
            root.add("permissions", perms);

            sendJson(exchange, 200, root.toString());
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

            // Telemetry carries per-user permissions and drains the pending-command
            // queue, so only backend servers presenting the shared secret are trusted.
            if (!isTrustedTelemetrySource(exchange)) {
                logger.warn("Rejected unauthenticated telemetry push from {}", exchange.getRemoteAddress());
                sendJson(exchange, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }

            try {
                String json;
                try {
                    json = HttpSecurity.readBody(exchange, HttpSecurity.MAX_TELEMETRY_BODY_BYTES);
                } catch (HttpSecurity.BodyTooLargeException e) {
                    sendJson(exchange, 413, "{\"error\":\"Payload too large\"}");
                    return;
                }
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

                // Poll pending commands for this backend server
                JsonObject resp = new JsonObject();
                resp.addProperty("status", "ok");
                JsonArray cmdsArr = new JsonArray();
                registry.getServer(targetServer).ifPresent(st -> {
                    for (String cmd : st.pollPendingCommands()) {
                        cmdsArr.add(cmd);
                    }
                });
                resp.add("pending_commands", cmdsArr);

                sendJson(exchange, 200, resp.toString());
            } catch (Exception e) {
                logger.warn("Error handling HTTP telemetry push: {}", e.getMessage());
                sendJson(exchange, 500, "{\"error\":\"Internal error\"}");
            }
        }
    }

    private class ApiConsoleLogsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthenticated(exchange)) {
                sendJson(exchange, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }
            if (rejectWhenAuthDisabled(exchange)) {
                return;
            }
            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String serverId = params.get("server");
            if (serverId == null || serverId.isBlank()) {
                sendJson(exchange, 400, "{\"error\":\"Missing server query parameter\"}");
                return;
            }

            String token = getSessionToken(exchange);
            String username = sessionManager.getUsername(token);
            if (username == null) {
                sendJson(exchange, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }
            boolean isSuper = userManager.isSuperAdmin(username);

            Optional<ServerTelemetryState> stOpt = registry.getServer(serverId);
            if (stOpt.isEmpty()) {
                sendJson(exchange, 404, "{\"error\":\"Server not found\"}");
                return;
            }

            ServerTelemetryState state = stOpt.get();
            if (!state.hasPermission(username, "console_view", isSuper)) {
                sendJson(exchange, 403, "{\"error\":\"Access denied: missing console_view permission\"}");
                return;
            }

            JsonObject resp = new JsonObject();
            resp.addProperty("status", "ok");
            resp.addProperty("server", serverId);
            JsonArray arr = new JsonArray();
            for (String line : state.getConsoleLogs()) {
                arr.add(line);
            }
            resp.add("logs", arr);
            sendJson(exchange, 200, resp.toString());
        }
    }

    private class ApiConsoleCommandHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthenticated(exchange)) {
                sendJson(exchange, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            if (rejectWhenAuthDisabled(exchange) || rejectCrossOrigin(exchange)) {
                return;
            }

            String token = getSessionToken(exchange);
            String username = sessionManager.getUsername(token);
            if (username == null) {
                sendJson(exchange, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }
            boolean isSuper = userManager.isSuperAdmin(username);

            String body = readBodyOrReject(exchange);
            if (body == null) {
                return;
            }
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            Map<String, String> params = parseRequestBody(body, contentType);

            String serverId = params.getOrDefault("server", params.getOrDefault("server_id", "")).trim();
            String command = params.getOrDefault("command", params.getOrDefault("cmd", "")).trim();

            if (serverId.isEmpty() || command.isEmpty()) {
                sendJson(exchange, 400, "{\"error\":\"Missing server or command parameter\"}");
                return;
            }
            if (command.length() > 1000 || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0) {
                sendJson(exchange, 400, "{\"error\":\"Invalid command\"}");
                return;
            }

            Optional<ServerTelemetryState> stOpt = registry.getServer(serverId);
            if (stOpt.isEmpty()) {
                sendJson(exchange, 404, "{\"error\":\"Server not found\"}");
                return;
            }

            ServerTelemetryState state = stOpt.get();
            if (!state.hasPermission(username, "console_command", isSuper)) {
                sendJson(exchange, 403, "{\"error\":\"Access denied: missing console_command permission\"}");
                return;
            }

            state.queueCommand(command);
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            state.addManualLog("[" + time + " " + username + "] > " + command);

            JsonObject resp = new JsonObject();
            resp.addProperty("status", "ok");
            resp.addProperty("message", "Command dispatched to server");
            sendJson(exchange, 200, resp.toString());
        }
    }

    private class ApiPasswordHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthenticated(exchange)) {
                sendJson(exchange, 401, "{\"success\":false,\"message\":\"Unauthorized\"}");
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"success\":false,\"message\":\"Method Not Allowed\"}");
                return;
            }
            if (rejectWhenAuthDisabled(exchange) || rejectCrossOrigin(exchange)) {
                return;
            }

            String token = getSessionToken(exchange);
            String username = sessionManager.getUsername(token);
            if (username == null) {
                sendJson(exchange, 401, "{\"success\":false,\"message\":\"Unauthorized\"}");
                return;
            }

            // A stolen session must not be usable to brute-force the current password.
            String limiterKey = "password:" + username.toLowerCase();
            if (loginRateLimiter.retryAfterMs(limiterKey) > 0) {
                sendJson(exchange, 429, "{\"success\":false,\"message\":\"Too many failed attempts. Try again later.\"}");
                return;
            }

            String body = readBodyOrReject(exchange);
            if (body == null) {
                return;
            }
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            Map<String, String> params = parseRequestBody(body, contentType);

            String oldPassword = params.getOrDefault("old_password", params.getOrDefault("current_password", ""));
            String newPassword = params.getOrDefault("new_password", params.getOrDefault("password", ""));

            if (newPassword.isBlank()) {
                sendJson(exchange, 400, "{\"success\":false,\"message\":\"New password cannot be empty\"}");
                return;
            }

            if (newPassword.length() < PasswordSecurity.MIN_PASSWORD_LENGTH) {
                sendJson(exchange, 400, "{\"success\":false,\"message\":\"Password must be at least "
                        + PasswordSecurity.MIN_PASSWORD_LENGTH + " characters long\"}");
                return;
            }

            if (oldPassword.isEmpty()) {
                sendJson(exchange, 400, "{\"success\":false,\"message\":\"Current password is required\"}");
                return;
            }
            if (!userManager.verifyPassword(username, oldPassword)) {
                loginRateLimiter.recordFailure(limiterKey);
                sendJson(exchange, 403, "{\"success\":false,\"message\":\"Current password is incorrect\"}");
                return;
            }
            loginRateLimiter.recordSuccess(limiterKey);

            boolean ok = userManager.updatePassword(username, newPassword);
            if (ok) {
                // Every session of this user was revoked by the password change; issue a fresh one
                // so only the browser that changed the password stays signed in.
                sessionManager.invalidateUser(username);
                int days = Math.max(1, config.getRememberMeDays());
                String freshToken = sessionManager.createSession(username, days);
                exchange.getResponseHeaders().set("Set-Cookie", "pineapple_session=" + freshToken + "; Path=/; Max-Age="
                        + ((long) days * 86400L) + "; HttpOnly; SameSite=Lax" + HttpSecurity.secureCookieSuffix(exchange));
                sendJson(exchange, 200, "{\"success\":true,\"message\":\"Password successfully updated. Other sessions were signed out.\"}");
            } else {
                sendJson(exchange, 500, "{\"success\":false,\"message\":\"Failed to update password\"}");
            }
        }
    }

    private class ApiLangHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            JsonObject root = new JsonObject();
            root.addProperty("current", config.getDefaultLanguage());

            JsonArray arr = new JsonArray();
            for (String c : DEFAULT_LANGUAGES) {
                String[] meta = LANG_META.get(c);
                if (meta == null) {
                    continue;
                }
                JsonObject item = new JsonObject();
                item.addProperty("code", c);
                item.addProperty("name", meta[0]);
                item.addProperty("native", meta[1]);
                item.addProperty("flag", meta[2]);
                if ("ar".equals(c)) {
                    item.addProperty("rtl", true);
                }
                arr.add(item);
            }
            root.add("languages", arr);

            sendJson(exchange, 200, root.toString());
        }
    }

    private void serveLangPack(HttpExchange exchange, String code) throws IOException {
        if (!HttpSecurity.isValidLangCode(code)) {
            sendJson(exchange, 404, "{\"error\":\"Language pack not found\"}");
            return;
        }
        String fileName = code + ".json";
        if (dataDirectory != null) {
            Path externalLang = dataDirectory.resolve("lang").resolve(fileName);
            if (Files.isRegularFile(externalLang)) {
                byte[] data = Files.readAllBytes(externalLang);
                sendResponse(exchange, 200, "application/json; charset=utf-8", data);
                return;
            }
        }

        try (InputStream in = getClass().getResourceAsStream("/lang/" + fileName)) {
            if (in != null) {
                byte[] data = in.readAllBytes();
                sendResponse(exchange, 200, "application/json; charset=utf-8", data);
                return;
            }
        }

        sendJson(exchange, 404, "{\"error\":\"Language pack not found\"}");
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String contentType, byte[] data) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
        exchange.sendResponseHeaders(statusCode, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private class StaticLangHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/lang/")) {
                String file = path.substring("/lang/".length());
                if (file.endsWith(".json")) {
                    String code = file.substring(0, file.length() - 5);
                    serveLangPack(exchange, code.toLowerCase());
                    return;
                }
            }
            sendJson(exchange, 404, "{\"error\":\"File not found\"}");
        }
    }

    private class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/static/")) {
                path = path.substring(7);
            }
            String rel = path.startsWith("/") ? path.substring(1) : path;
            String resourcePath = "/web/" + rel;

            String contentType = "text/plain";
            if (rel.endsWith(".css")) contentType = "text/css; charset=utf-8";
            else if (rel.endsWith(".js")) contentType = "application/javascript; charset=utf-8";
            else if (rel.endsWith(".svg")) contentType = "image/svg+xml";
            else if (rel.endsWith(".png")) contentType = "image/png";
            else if (rel.endsWith(".json")) contentType = "application/json; charset=utf-8";

            serveStatic(exchange, resourcePath, contentType);
        }
    }

    private class FaviconHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            serveStatic(exchange, "/web/favicon.ico", "image/x-icon");
        }
    }
}
