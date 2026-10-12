package me.pinkysha.pineapple.velocity.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-client brute-force protection for the login endpoint.
 * After {@link #MAX_FAILURES} failed attempts within {@link #WINDOW_MS}
 * the client is locked out for {@link #LOCKOUT_MS}.
 */
public class LoginRateLimiter {

    static final int MAX_FAILURES = 5;
    static final long WINDOW_MS = 10 * 60_000L;
    static final long LOCKOUT_MS = 5 * 60_000L;

    private static final class Attempts {
        int failures;
        long windowStart;
        long lockedUntil;
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    /**
     * @return milliseconds the client still has to wait, or 0 if it may try now.
     */
    public long retryAfterMs(String client) {
        Attempts a = attempts.get(client);
        if (a == null) return 0;
        synchronized (a) {
            return Math.max(0, a.lockedUntil - System.currentTimeMillis());
        }
    }

    public void recordFailure(String client) {
        long now = System.currentTimeMillis();
        Attempts a = attempts.computeIfAbsent(client, k -> new Attempts());
        synchronized (a) {
            if (now - a.windowStart > WINDOW_MS) {
                a.windowStart = now;
                a.failures = 0;
            }
            a.failures++;
            if (a.failures >= MAX_FAILURES) {
                a.lockedUntil = now + LOCKOUT_MS;
                a.failures = 0;
                a.windowStart = now;
            }
        }
        if (attempts.size() > 10_000) {
            cleanup();
        }
    }

    public void recordSuccess(String client) {
        attempts.remove(client);
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(e -> {
            Attempts a = e.getValue();
            synchronized (a) {
                return a.lockedUntil < now && now - a.windowStart > WINDOW_MS;
            }
        });
    }
}
