package com.app.limitledger.ratelimit;

public record RateLimitDecision(
        boolean allowed,
        int limit,
        int remaining,
        long retryAfterSeconds,
        long resetAtEpochSeconds) {
}
