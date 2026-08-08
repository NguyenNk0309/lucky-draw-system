package com.marketplace.campaign.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.campaign.domain.Campaign;
import com.marketplace.campaign.domain.Reward;
import com.marketplace.campaign.domain.port.CampaignRepository;
import com.marketplace.campaign.domain.port.OutboxRepository;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCampaignRepository implements CampaignRepository, OutboxRepository {
    private static final RowMapper<Campaign> ROW = (rs, n) -> new Campaign(
            rs.getString("id"), rs.getString("seller_id"), rs.getString("name"),
            Campaign.Status.valueOf(rs.getString("status")), rs.getInt("max_entries_per_user"),
            rs.getTimestamp("start_at").toInstant(), rs.getTimestamp("end_at").toInstant(),
            new Reward(Reward.Type.valueOf(rs.getString("reward_type")), rs.getString("reward_reference")),
            rs.getString("winner_entry_id"), rs.getString("snapshot_hash"));

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcCampaignRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Campaign insert(Campaign campaign) {
        jdbc.update("""
                INSERT INTO campaigns
                  (id,seller_id,name,status,max_entries_per_user,start_at,end_at,reward_type,reward_reference)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, campaign.id(), campaign.sellerId(), campaign.name(), campaign.status().name(),
                campaign.maxEntriesPerUser(), Timestamp.from(campaign.startAt()), Timestamp.from(campaign.endAt()),
                campaign.reward().type().name(), campaign.reward().reference());
        return campaign;
    }

    @Override public List<Campaign> findAll() {
        return jdbc.query("SELECT * FROM campaigns ORDER BY created_at DESC", ROW);
    }

    @Override public Optional<Campaign> findById(String id) {
        try { return Optional.ofNullable(jdbc.queryForObject("SELECT * FROM campaigns WHERE id=?", ROW, id)); }
        catch (EmptyResultDataAccessException exception) { return Optional.empty(); }
    }

    @Override public boolean activate(String id, String sellerId) {
        return jdbc.update("UPDATE campaigns SET status='ACTIVE' WHERE id=? AND seller_id=? AND status='DRAFT'",
                id, sellerId) == 1;
    }

    @Override public boolean cancel(String id, String sellerId) {
        return jdbc.update("UPDATE campaigns SET status='CANCELLED' WHERE id=? AND seller_id=? AND status IN ('DRAFT','ACTIVE')",
                id, sellerId) == 1;
    }

    @Override public void append(String id, String aggregateId, String eventType, Object event) {
        try {
            jdbc.update("INSERT INTO outbox (id,aggregate_id,event_type,payload) VALUES (?,?,?,?)",
                    id, aggregateId, eventType, json.writeValueAsString(event));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Cannot serialize campaign event", exception);
        }
    }
}
