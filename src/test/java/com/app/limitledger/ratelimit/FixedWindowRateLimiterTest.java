package com.app.limitledger.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.app.limitledger.config.RateLimitProperties;

class FixedWindowRateLimiterTest {
    @Test
    void permitsRequestsUpToConfiguredLimitThenRejects() {
        FixedWindowRateLimiter limiter = limiter(3, 60, new InMemoryCounter(), 120);

        assertTrue(limiter.check("client-a").allowed());
        assertTrue(limiter.check("client-a").allowed());
        RateLimitDecision third = limiter.check("client-a");
        assertTrue(third.allowed());
        assertEquals(0, third.remaining());
        assertFalse(limiter.check("client-a").allowed());
    }

    @Test
    void tracksClientsIndependentlyAndStartsANewWindowAtBoundary() {
        InMemoryCounter counter = new InMemoryCounter();
        FixedWindowRateLimiter firstWindow = limiter(1, 60, counter, 119);
        FixedWindowRateLimiter nextWindow = limiter(1, 60, counter, 120);

        assertTrue(firstWindow.check("client-a").allowed());
        assertFalse(firstWindow.check("client-a").allowed());
        assertTrue(firstWindow.check("client-b").allowed());
        assertTrue(nextWindow.check("client-a").allowed());
    }

    @Test
    void reportsRetryAndResetTimes() {
        FixedWindowRateLimiter limiter = limiter(2, 60, new InMemoryCounter(), 121);

        RateLimitDecision decision = limiter.check("client-a");

        assertEquals(59, decision.retryAfterSeconds());
        assertEquals(180, decision.resetAtEpochSeconds());
    }

    private static FixedWindowRateLimiter limiter(int requests, int windowSeconds, RateLimitCounter counter,
            long epochSeconds) {
        return new FixedWindowRateLimiter(counter, new RateLimitProperties(requests, windowSeconds),
                Clock.fixed(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC));
    }

    private static final class InMemoryCounter implements RateLimitCounter {
        private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

        @Override
        public long increment(String clientId, long windowNumber, long ttlSeconds) {
            return counters.computeIfAbsent(clientId + ":" + windowNumber, ignored -> new AtomicLong())
                    .incrementAndGet();
        }
    }
}
