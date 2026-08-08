package com.marketplace.reward.service;

import com.marketplace.events.WinnerPicked;
import com.marketplace.reward.domain.RewardClaim;
import com.marketplace.reward.infrastructure.RewardClaimRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RewardDeliveryService {
    private final RewardClaimRepository claims;
    private final RewardStrategyFactory strategies;

    public RewardDeliveryService(RewardClaimRepository claims, RewardStrategyFactory strategies) {
        this.claims = claims;
        this.strategies = strategies;
    }

    public void deliver(WinnerPicked event) {
        claims.createOnce(event).ifPresent(claim -> {
            var result = strategies.forReward(event.reward()).deliver(event.winnerUserId(), event.campaignId());
            claims.markDelivered(claim.id(), result.deliveryReference());
        });
    }

    public List<RewardClaim> list(String userId) {
        return claims.findByUser(userId);
    }
}

