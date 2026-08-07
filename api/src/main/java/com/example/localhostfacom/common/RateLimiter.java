package com.example.localhostfacom.common;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Fixed-window counter held in process memory. It is per instance and resets on
 * deploy, which is adequate for a single instance serving one room — a speed bump
 * against accidents and casual abuse, not a real defence. If this ever runs on more
 * than one instance, it needs to move to shared state.
 */
@Component
public class RateLimiter {

    private record Window(Instant startedAt, AtomicInteger count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int limit, Duration window) {
        Instant now = Instant.now();
        Window current = windows.compute(key, (k, existing) -> {
            if (existing == null || existing.startedAt().plus(window).isBefore(now)) {
                return new Window(now, new AtomicInteger(0));
            }
            return existing;
        });
        return current.count().incrementAndGet() <= limit;
    }
}
