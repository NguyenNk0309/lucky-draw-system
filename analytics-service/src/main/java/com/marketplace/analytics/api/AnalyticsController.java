package com.marketplace.analytics.api;

import com.marketplace.analytics.domain.CampaignStats;
import com.marketplace.analytics.domain.MyResult;
import com.marketplace.analytics.service.AnalyticsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/campaigns/{campaignId}")
public class AnalyticsController {
    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/stats")
    public CampaignStats stats(@PathVariable String campaignId,
            @RequestHeader("X-Demo-User") String sellerId,
            @RequestHeader("X-Demo-Role") String role) {
        if (!"SELLER".equals(role)) throw new AnalyticsService.ForbiddenException();
        return service.stats(campaignId, sellerId);
    }

    @GetMapping("/me")
    public MyResult mine(@PathVariable String campaignId,
            @RequestHeader("X-Demo-User") String userId,
            @RequestHeader("X-Demo-Role") String role) {
        if (!"CUSTOMER".equals(role)) throw new AnalyticsService.ForbiddenException();
        return service.mine(campaignId, userId);
    }

    @ExceptionHandler(AnalyticsService.NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void notFound() {}

    @ExceptionHandler(AnalyticsService.ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    void forbidden() {}
}

