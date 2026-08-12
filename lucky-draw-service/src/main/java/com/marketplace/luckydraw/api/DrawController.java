package com.marketplace.luckydraw.api;

import com.marketplace.luckydraw.domain.DrawResult;
import com.marketplace.luckydraw.service.DrawService;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/campaigns/{campaignId}/draw")
public class DrawController {
    private final DrawService service;

    public DrawController(DrawService service) {
        this.service = service;
    }

    @PostMapping
    public DrawResult draw(@PathVariable String campaignId,
            @RequestHeader("X-Demo-User") String sellerId,
            @RequestHeader("X-Demo-Role") String role,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        DemoAuth.require(role, "SELLER");
        return service.draw(campaignId, sellerId,
                correlationId == null ? UUID.randomUUID().toString() : correlationId);
    }
}
