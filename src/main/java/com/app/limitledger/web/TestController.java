package com.app.limitledger.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {
    @GetMapping("/api/test")
    public TestResponse test() {
        return new TestResponse("Request allowed", "API Rate Limiter is protecting this endpoint.");
    }

    public record TestResponse(String status, String message) {
    }
}
