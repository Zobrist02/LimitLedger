package com.app.limitledger.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.app.limitledger.config.RateLimitProperties;

@Testcontainers(disabledWithoutDocker = true)
class RedisRateLimitConcurrencyTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Test
    void counterExpiresAfterItsWindowTtl() throws Exception {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        try {
            StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
            template.afterPropertiesSet();
            RedisRateLimitCounter counter = new RedisRateLimitCounter(template);
            String clientId = "expiry-test-" + UUID.randomUUID();

            assertEquals(1, counter.increment(clientId, 999_999, 1));
            Thread.sleep(1_300);
            assertEquals(1, counter.increment(clientId, 999_999, 1));
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void concurrentRequestsCannotExceedConfiguredLimit() throws Exception {
        RedisStandaloneConfiguration redisConfiguration = new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(redisConfiguration);
        connectionFactory.afterPropertiesSet();
        try {
            StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
            template.afterPropertiesSet();
            FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(
                    new RedisRateLimitCounter(template), new RateLimitProperties(10, 60),
                    Clock.fixed(Instant.ofEpochSecond(120), ZoneOffset.UTC));

            int requestCount = 100;
            CountDownLatch ready = new CountDownLatch(20);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<RateLimitDecision>> results = new ArrayList<>();
            try (var executor = Executors.newFixedThreadPool(20)) {
                String clientId = "concurrent-test-" + UUID.randomUUID();
                for (int i = 0; i < requestCount; i++) {
                    results.add(executor.submit(() -> {
                        if (ready.getCount() > 0) {
                            ready.countDown();
                        }
                        start.await();
                        return limiter.check(clientId);
                    }));
                }
                ready.await();
                start.countDown();
                int allowed = 0;
                int rejected = 0;
                for (Future<RateLimitDecision> result : results) {
                    if (result.get().allowed()) {
                        allowed++;
                    } else {
                        rejected++;
                    }
                }
                assertEquals(10, allowed);
                assertEquals(90, rejected);
            }
        } finally {
            connectionFactory.destroy();
        }
    }
}
