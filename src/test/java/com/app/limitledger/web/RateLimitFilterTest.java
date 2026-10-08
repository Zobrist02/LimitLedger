package com.app.limitledger.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.app.limitledger.ratelimit.FixedWindowRateLimiter;
import com.app.limitledger.ratelimit.RateLimitDecision;

import tools.jackson.databind.json.JsonMapper;

class RateLimitFilterTest {
    private final FixedWindowRateLimiter limiter = mock(FixedWindowRateLimiter.class);
    private final RateLimitFilter filter = new RateLimitFilter(limiter, JsonMapper.builder().build());

    @Test
    void rejectsRequestsWithoutClientKey() throws Exception {
        MockHttpServletRequest request = apiRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(400, response.getStatus());
        assertEquals("application/json", response.getContentType());
        verifyNoInteractions(limiter);
        assertNull(chain.getRequest());
    }

    @Test
    void returns429AndRetryHeadersWhenLimitIsExceeded() throws Exception {
        MockHttpServletRequest request = apiRequest();
        request.addHeader("X-API-Key", "demo-client");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(limiter.check("demo-client")).thenReturn(new RateLimitDecision(false, 5, 0, 12, 1_800_000_000));

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertEquals("5", response.getHeader("X-RateLimit-Limit"));
        assertEquals("0", response.getHeader("X-RateLimit-Remaining"));
        assertEquals("1800000000", response.getHeader("X-RateLimit-Reset"));
        assertEquals("12", response.getHeader("Retry-After"));
        assertNull(chain.getRequest());
        verify(limiter).check("demo-client");
    }

    @Test
    void passesAllowedRequestsToTheEndpoint() throws Exception {
        MockHttpServletRequest request = apiRequest();
        request.addHeader("X-API-Key", "demo-client");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(limiter.check("demo-client")).thenReturn(new RateLimitDecision(true, 5, 4, 60, 1_800_000_000));

        filter.doFilter(request, response, chain);

        assertEquals(request, chain.getRequest());
        assertEquals("4", response.getHeader("X-RateLimit-Remaining"));
        assertEquals(200, response.getStatus());
        verify(limiter).check("demo-client");
    }

    @Test
    void failsClosedWhenRedisCounterCannotBeRead() throws Exception {
        MockHttpServletRequest request = apiRequest();
        request.addHeader("X-API-Key", "demo-client");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(limiter.check("demo-client")).thenThrow(new IllegalStateException("Redis unavailable"));

        filter.doFilter(request, response, chain);

        assertEquals(503, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertNull(chain.getRequest());
        verify(limiter).check("demo-client");
    }

    private static MockHttpServletRequest apiRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        return request;
    }
}
