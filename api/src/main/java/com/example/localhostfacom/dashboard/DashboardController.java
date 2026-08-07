package com.example.localhostfacom.dashboard;

import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.common.RateLimiter;
import com.example.localhostfacom.dashboard.dto.DashboardResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/dashboard")
public class DashboardController {

    private final DashboardService service;
    private final RateLimiter rateLimiter;

    public DashboardController(DashboardService service, RateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping
    public DashboardResponse get(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest http) {
        // Runs several aggregate queries per call, so it is throttled tighter than the
        // catalog — a scripted loop against this endpoint is real database load.
        if (!rateLimiter.tryAcquire("dashboard:" + http.getRemoteAddr(), 30, Duration.ofMinutes(1))) {
            throw ApiException.tooManyRequests("rate-limited", "Too many requests; please wait a moment");
        }

        return service.build(page, size);
    }
}
