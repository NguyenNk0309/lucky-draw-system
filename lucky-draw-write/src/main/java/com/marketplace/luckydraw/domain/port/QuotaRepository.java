package com.marketplace.luckydraw.domain.port;

public interface QuotaRepository {
    boolean tryReserve(String campaignId, String userId, int limit);
}

