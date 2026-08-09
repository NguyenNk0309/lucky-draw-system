package com.marketplace.reward.infrastructure;

import com.marketplace.events.WinnerPicked;
import com.marketplace.reward.domain.RewardClaim;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RewardClaimRepository {
    private final JdbcTemplate jdbc;

    public RewardClaimRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Optional<RewardClaim> createOnce(WinnerPicked event) {
        int first = jdbc.update("INSERT IGNORE INTO processed_events (event_id,consumer_name) VALUES (?,'reward-service')",
                event.eventId().toString());
        if (first == 0) return Optional.empty();
        var claim = new RewardClaim(UUID.randomUUID().toString(), event.campaignId(), event.winnerEntryId(),
                event.winnerUserId(), event.reward().type().name(), event.reward().reference(), null, null);
        jdbc.update("""
                INSERT INTO reward_claims (id,campaign_id,winner_entry_id,winner_user_id,reward_type,reference)
                VALUES (?,?,?,?,?,?)
                """, claim.id(), claim.campaignId(), claim.winnerEntryId(), claim.winnerUserId(),
                claim.rewardType(), claim.reference());
        return Optional.of(claim);
    }

    public void markDelivered(String claimId, String deliveryReference) {
        jdbc.update("UPDATE reward_claims SET delivery_reference=?, delivered_at=NOW(6) WHERE id=? AND delivered_at IS NULL",
                deliveryReference, claimId);
    }

    public List<RewardClaim> findByUser(String userId) {
        return jdbc.query("SELECT * FROM reward_claims WHERE winner_user_id=? ORDER BY created_at DESC", (rs, n) ->
                new RewardClaim(rs.getString("id"), rs.getString("campaign_id"), rs.getString("winner_entry_id"),
                        rs.getString("winner_user_id"), rs.getString("reward_type"), rs.getString("reference"),
                        rs.getString("delivery_reference"),
                        rs.getTimestamp("delivered_at") == null ? null : rs.getTimestamp("delivered_at").toInstant()), userId);
    }
}
