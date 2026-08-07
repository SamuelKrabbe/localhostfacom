package com.example.localhostfacom.common;

import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    @Test
    void allowsUpToTheLimitThenRefuses() {
        RateLimiter limiter = new RateLimiter();

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("1.2.3.4", 3, Duration.ofMinutes(1))).isTrue();
        }
        assertThat(limiter.tryAcquire("1.2.3.4", 3, Duration.ofMinutes(1))).isFalse();
    }

    @Test
    void keepsSeparateCountsPerKey() {
        RateLimiter limiter = new RateLimiter();

        assertThat(limiter.tryAcquire("a", 1, Duration.ofMinutes(1))).isTrue();
        assertThat(limiter.tryAcquire("a", 1, Duration.ofMinutes(1))).isFalse();
        assertThat(limiter.tryAcquire("b", 1, Duration.ofMinutes(1))).isTrue();
    }

    @Test
    void resetsAfterTheWindowElapses() throws Exception {
        RateLimiter limiter = new RateLimiter();

        assertThat(limiter.tryAcquire("a", 1, Duration.ofMillis(50))).isTrue();
        assertThat(limiter.tryAcquire("a", 1, Duration.ofMillis(50))).isFalse();
        Thread.sleep(60);
        assertThat(limiter.tryAcquire("a", 1, Duration.ofMillis(50))).isTrue();
    }
}
