package com.app.limitledger.ratelimit;

public interface RateLimitCounter {
    long increment(String clientId, long windowNumber, long ttlSeconds);
}
