package me.pinkysha.pineapple.velocity.server;

import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.regex.Pattern;

/**
 * Shared request-hardening helpers for the embedded web server.
 */
public final class HttpSecurity {

    public static final int MAX_BODY_BYTES = 64 * 1024;
    /** Telemetry snapshots carry player lists and console lines, so they get a larger budget. */
    public static final int MAX_TELEMETRY_BODY_BYTES = 4 * 1024 * 1024;

    private static final Pattern LANG_CODE = Pattern.compile("^[a-z]{2,8}(?:[-_][a-z0-9]{2,8})?$");

    private HttpSecurity() {
    }

    public static final class BodyTooLargeException extends IOException {
        public BodyTooLargeException(int limit) {
            super("Request body exceeds " + limit + " bytes");
        }
    }

    /**
     * Reads the request body, refusing anything larger than {@link #MAX_BODY_BYTES}.
     */
    public static String readBody(HttpExchange exchange) throws IOException {
        return readBody(exchange, MAX_BODY_BYTES);
    }

    public static String readBody(HttpExchange exchange, int limit) throws IOException {
        String declared = exchange.getRequestHeaders().getFirst("Content-Length");
        if (declared != null) {
            try {
                if (Long.parseLong(declared.trim()) > limit) {
                    throw new BodyTooLargeException(limit);
                }
            } catch (NumberFormatException ignored) {}
        }
        try (InputStream in = exchange.getRequestBody()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > limit) {
                    throw new BodyTooLargeException(limit);
                }
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    /**
     * Resolves a request-relative path strictly inside {@code root}.
     * Returns null for traversal attempts or anything that escapes the root.
     */
    public static Path resolveInside(Path root, String relative) {
        if (root == null || !isSafeRelativePath(relative)) return null;
        Path base = root.toAbsolutePath().normalize();
        Path resolved = base.resolve(relative).normalize();
        return resolved.startsWith(base) && !resolved.equals(base) ? resolved : null;
    }

    /**
     * True for a non-empty relative path with no "..", ".", backslash, NUL or leading slash.
     */
    public static boolean isSafeRelativePath(String relative) {
        if (relative == null || relative.isEmpty() || relative.startsWith("/")) return false;
        if (relative.contains("\\") || relative.indexOf('\0') >= 0 || relative.contains(":")) return false;
        for (String segment : relative.split("/", -1)) {
            if (segment.isEmpty() || segment.equals("..") || segment.equals(".")) return false;
        }
        return true;
    }

    public static boolean isValidLangCode(String code) {
        return code != null && LANG_CODE.matcher(code).matches();
    }

    /**
     * Rejects cross-origin state-changing requests: when the browser sends an Origin
     * (or Referer) header, its host must match the Host header.
     */
    public static boolean isSameOrigin(HttpExchange exchange) {
        String host = exchange.getRequestHeaders().getFirst("Host");
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null || origin.isBlank() || "null".equals(origin)) {
            origin = exchange.getRequestHeaders().getFirst("Referer");
        }
        if (origin == null || origin.isBlank()) {
            // Non-browser clients do not send Origin; browsers always do on cross-site POST.
            return true;
        }
        if (host == null) return false;
        try {
            String originAuthority = URI.create(origin.trim()).getRawAuthority();
            return originAuthority != null && originAuthority.equalsIgnoreCase(host.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Returns "; Secure" when the request reached us over HTTPS via a reverse proxy.
     */
    public static String secureCookieSuffix(HttpExchange exchange) {
        String proto = exchange.getRequestHeaders().getFirst("X-Forwarded-Proto");
        return proto != null && proto.trim().equalsIgnoreCase("https") ? "; Secure" : "";
    }

    /**
     * Restricts a file holding secrets to owner read/write where the filesystem supports it.
     */
    public static void restrictToOwner(Path file) {
        try {
            if (Files.exists(file)) {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
            }
        } catch (UnsupportedOperationException | IOException ignored) {}
    }

    public static String clientAddress(HttpExchange exchange) {
        return exchange.getRemoteAddress() != null && exchange.getRemoteAddress().getAddress() != null
                ? exchange.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }
}
