package com.app.limitledger.ratelimit;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.app.limitledger.config.RateLimitProperties;

@Service
public class FixedWindowRateLimiter {
    private final RateLimitCounter counter;
    private final RateLimitProperties properties;
    private final Clock clock;

    @Autowired
    public FixedWindowRateLimiter(RateLimitCounter counter, RateLimitProperties properties) {
        this(counter, properties, Clock.systemUTC());
    }

    FixedWindowRateLimiter(RateLimitCounter counter, RateLimitProperties properties, Clock clock) {
        this.counter = counter;
        this.properties = properties;
        this.clock = clock;
    }

    public RateLimitDecision check(String clientId) {
        long now = clock.instant().getEpochSecond();
        long windowSeconds = properties.windowSeconds();
        long windowNumber = Math.floorDiv(now, windowSeconds);
        long secondsUntilWindowEnd = windowSeconds - Math.floorMod(now, windowSeconds);
        long count = counter.increment(clientId, windowNumber, secondsUntilWindowEnd);
        int remaining = (int) Math.max(0, properties.requests() - count);
        return new RateLimitDecision(count <= properties.requests(), properties.requests(), remaining,
                secondsUntilWindowEnd, now + secondsUntilWindowEnd);
    }
}
