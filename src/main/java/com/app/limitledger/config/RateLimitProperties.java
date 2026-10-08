package com.app.limitledger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;

@Validated
@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(
        @Min(1) int requests,
        @Min(1) int windowSeconds) {
}
