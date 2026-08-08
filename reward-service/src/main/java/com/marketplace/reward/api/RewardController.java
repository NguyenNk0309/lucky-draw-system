package com.marketplace.reward.api;

import com.marketplace.reward.domain.RewardClaim;
import com.marketplace.reward.service.RewardDeliveryService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rewards")
public class RewardController {
    private final RewardDeliveryService service;

    public RewardController(RewardDeliveryService service) {
        this.service = service;
    }

    @GetMapping
    public List<RewardClaim> list(@RequestHeader("X-Demo-User") String userId,
            @RequestHeader("X-Demo-Role") String role) {
        if (!"CUSTOMER".equals(role)) throw new ForbiddenException();
        return service.list(userId);
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    private static class ForbiddenException extends RuntimeException {}
}

