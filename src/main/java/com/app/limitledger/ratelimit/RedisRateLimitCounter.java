package com.app.limitledger.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisRateLimitCounter implements RateLimitCounter {
    private static final byte[] SCRIPT = ("local count = redis.call('INCR', KEYS[1])\n"
            + "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end\n"
            + "return count").getBytes(StandardCharsets.UTF_8);

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimitCounter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long increment(String clientId, long windowNumber, long ttlSeconds) {
        String key = "rate_limit:" + sha256(clientId) + ":" + windowNumber;
        Long count = redisTemplate.execute((RedisCallback<Long>) connection -> connection.eval(
                SCRIPT,
                org.springframework.data.redis.connection.ReturnType.INTEGER,
                1,
                key.getBytes(StandardCharsets.UTF_8),
                Long.toString(ttlSeconds).getBytes(StandardCharsets.UTF_8)));
        if (count == null) {
            throw new IllegalStateException("Redis did not return a counter value");
        }
        return count;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
