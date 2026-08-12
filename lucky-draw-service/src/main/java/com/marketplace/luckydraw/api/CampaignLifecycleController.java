package com.marketplace.luckydraw.api;

import com.marketplace.luckydraw.domain.Campaign;
import com.marketplace.luckydraw.service.CampaignLifecycleService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/campaigns/{id}")
public class CampaignLifecycleController {
    private final CampaignLifecycleService campaigns;
    public CampaignLifecycleController(CampaignLifecycleService campaigns) { this.campaigns = campaigns; }

    @PostMapping("/end")
    public Campaign end(@PathVariable String id, @RequestHeader("X-Demo-User") String sellerId,
            @RequestHeader("X-Demo-Role") String role) {
        DemoAuth.require(role, "SELLER");
        return campaigns.end(id, sellerId);
    }
}
