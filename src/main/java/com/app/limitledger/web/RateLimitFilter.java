package com.app.limitledger.web;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.app.limitledger.ratelimit.FixedWindowRateLimiter;
import com.app.limitledger.ratelimit.RateLimitDecision;
import tools.jackson.databind.json.JsonMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final String API_KEY_HEADER = "X-API-Key";

    private final FixedWindowRateLimiter rateLimiter;
    private final JsonMapper objectMapper;

    public RateLimitFilter(FixedWindowRateLimiter rateLimiter, JsonMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String clientId = request.getHeader(API_KEY_HEADER);
        if (clientId == null || clientId.isBlank()) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "missing_api_key",
                    "The X-API-Key header is required.");
            return;
        }

        RateLimitDecision decision;
        try {
            decision = rateLimiter.check(clientId);
        } catch (RuntimeException redisFailure) {
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "rate_limiter_unavailable",
                    "Rate limiting is temporarily unavailable.");
            return;
        }

        response.setHeader("X-RateLimit-Limit", Integer.toString(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", Integer.toString(decision.remaining()));
        response.setHeader("X-RateLimit-Reset", Long.toString(decision.resetAtEpochSeconds()));
        if (!decision.allowed()) {
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
            writeError(response, 429, "rate_limit_exceeded", "Too many requests. Try again later.");
            return;
        }
        chain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(code, message));
    }

    public record ErrorResponse(String error, String message) {
    }
}
