package com.marketplace.reward.infrastructure;

import com.marketplace.reward.domain.port.CouponIssuer;
import com.marketplace.reward.domain.port.Fulfillment;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class LocalRewardSystems implements Fulfillment, CouponIssuer {
    @Override
    public String reserveProduct(String productReference, String userId, String campaignId) {
        return "LOCAL-SHIPMENT-" + UUID.randomUUID();
    }

    @Override
    public String issueCoupon(String couponReference, String userId, String campaignId) {
        return couponReference + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}

