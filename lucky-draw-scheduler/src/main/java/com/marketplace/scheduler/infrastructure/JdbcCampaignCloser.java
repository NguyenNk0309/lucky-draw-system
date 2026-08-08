package com.marketplace.scheduler.infrastructure;

import com.marketplace.scheduler.domain.port.CampaignCloser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcCampaignCloser implements CampaignCloser {
    private final JdbcTemplate jdbc;

    public JdbcCampaignCloser(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> findDueCampaigns() {
        return jdbc.queryForList(
                "SELECT id FROM campaigns WHERE status='ACTIVE' AND end_at<=NOW(6) ORDER BY end_at LIMIT 50",
                String.class);
    }

    @Override
    @Transactional
    public void closeAndFreeze(String campaignId) {
        List<String> locked = jdbc.queryForList(
                "SELECT id FROM campaigns WHERE id=? AND status='ACTIVE' AND end_at<=NOW(6) FOR UPDATE",
                String.class, campaignId);
        if (locked.isEmpty()) return;

        jdbc.update("UPDATE campaigns SET status='ENDED' WHERE id=? AND status='ACTIVE'", campaignId);
        List<String> ids = jdbc.queryForList(
                "SELECT id FROM entries WHERE campaign_id=? ORDER BY seq", String.class, campaignId);
        String hash = sha256(String.join("\n", ids));
        jdbc.update("INSERT INTO draw_snapshots (campaign_id,total_entries,content_hash,frozen_at) VALUES (?,?,?,NOW(6))",
                campaignId, ids.size(), hash);
        for (int i = 0; i < ids.size(); i++) {
            jdbc.update("INSERT INTO draw_snapshot_items (campaign_id,idx,entry_id) VALUES (?,?,?)",
                    campaignId, i + 1L, ids.get(i));
        }
        String eventId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO outbox (id,aggregate_id,event_type,payload)
                SELECT ?, id, 'CampaignUpdated', JSON_OBJECT(
                  'eventId', ?, 'occurredAt', DATE_FORMAT(UTC_TIMESTAMP(6),'%Y-%m-%dT%H:%i:%s.%fZ'),
                  'aggregateId', id, 'correlationId', ?, 'campaignId', id, 'sellerId', seller_id,
                  'name', name, 'status', status, 'maxEntriesPerUser', max_entries_per_user,
                  'startAt', DATE_FORMAT(start_at,'%Y-%m-%dT%H:%i:%s.%fZ'),
                  'endAt', DATE_FORMAT(end_at,'%Y-%m-%dT%H:%i:%s.%fZ'),
                  'reward', JSON_OBJECT('type',reward_type,'reference',reward_reference))
                FROM campaigns WHERE id=?
                """, eventId, eventId, "scheduler-" + campaignId, campaignId);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
