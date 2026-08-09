package com.marketplace.luckydraw.infrastructure.mysql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.luckydraw.domain.Campaign;
import com.marketplace.luckydraw.domain.CampaignStatus;
import com.marketplace.luckydraw.domain.DrawSnapshot;
import com.marketplace.luckydraw.domain.Entry;
import com.marketplace.luckydraw.domain.Reward;
import com.marketplace.luckydraw.domain.Ticket;
import com.marketplace.luckydraw.domain.port.CampaignRepository;
import com.marketplace.luckydraw.domain.port.EntryRepository;
import com.marketplace.luckydraw.domain.port.OutboxRepository;
import com.marketplace.luckydraw.domain.port.QuotaRepository;
import com.marketplace.luckydraw.domain.port.SnapshotRepository;
import com.marketplace.luckydraw.domain.port.TicketRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWriteRepository implements CampaignRepository, TicketRepository, QuotaRepository,
        EntryRepository, SnapshotRepository, OutboxRepository {
    private static final RowMapper<Campaign> CAMPAIGN_ROW = (rs, n) -> new Campaign(
            rs.getString("id"), rs.getString("seller_id"), rs.getString("name"),
            CampaignStatus.valueOf(rs.getString("status")), rs.getInt("max_entries_per_user"),
            rs.getTimestamp("start_at").toInstant(), rs.getTimestamp("end_at").toInstant(),
            new Reward(Reward.Type.valueOf(rs.getString("reward_type")), rs.getString("reward_reference")),
            rs.getString("winner_entry_id"), rs.getString("snapshot_hash"));
    private static final RowMapper<Entry> ENTRY_ROW = (rs, n) -> new Entry(
            rs.getString("id"), rs.getString("campaign_id"), rs.getString("user_id"),
            rs.getString("ticket_id"), rs.getLong("seq"), rs.getTimestamp("submitted_at").toInstant(),
            rs.getInt("wheel_segment"), rs.getBoolean("reward_pending"));

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcWriteRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<Campaign> findAll() {
        return jdbc.query("SELECT * FROM campaigns ORDER BY created_at DESC", CAMPAIGN_ROW);
    }

    @Override
    public Optional<Campaign> findById(String id) {
        return one("SELECT * FROM campaigns WHERE id = ?", CAMPAIGN_ROW, id);
    }

    @Override
    public Optional<Campaign> lockShared(String id) {
        return one("SELECT * FROM campaigns WHERE id = ? FOR SHARE", CAMPAIGN_ROW, id);
    }

    @Override
    public Optional<Campaign> lockExclusive(String id) {
        return one("SELECT * FROM campaigns WHERE id = ? FOR UPDATE", CAMPAIGN_ROW, id);
    }

    @Override
    public boolean end(String id, String sellerId) {
        return jdbc.update("UPDATE campaigns SET status='ENDED' WHERE id=? AND seller_id=? AND status='ACTIVE'",
                id, sellerId) == 1;
    }

    @Override
    public boolean markDrawn(String id, String snapshotHash) {
        return jdbc.update("UPDATE campaigns SET status='DRAWN', snapshot_hash=? WHERE id=? AND status='ENDED'",
                snapshotHash, id) == 1;
    }

    @Override
    public void issueForOrder(String orderId, String userId) {
        jdbc.update("INSERT IGNORE INTO tickets (id, order_id, user_id) VALUES (?, ?, ?)",
                UUID.randomUUID().toString(), orderId, userId);
    }

    @Override
    public boolean consume(String ticketId, String userId, String campaignId, String entryId) {
        return jdbc.update("""
                UPDATE tickets SET status='CONSUMED', campaign_id=?, consumed_by_entry_id=?, consumed_at=NOW(6)
                 WHERE id=? AND user_id=? AND status='ISSUED'
                """, campaignId, entryId, ticketId, userId) == 1;
    }

    @Override
    public List<Ticket> findByUser(String userId) {
        return jdbc.query("SELECT * FROM tickets WHERE user_id=? ORDER BY issued_at DESC", (rs, n) -> new Ticket(
                rs.getString("id"), rs.getString("order_id"), rs.getString("user_id"),
                Ticket.Status.valueOf(rs.getString("status")), rs.getString("campaign_id"),
                rs.getString("consumed_by_entry_id"), rs.getTimestamp("issued_at").toInstant(),
                rs.getTimestamp("consumed_at") == null ? null : rs.getTimestamp("consumed_at").toInstant()), userId);
    }

    @Override
    public boolean tryReserve(String campaignId, String userId, int limit) {
        jdbc.update("INSERT IGNORE INTO user_entry_quota (campaign_id, user_id, used) VALUES (?, ?, 0)",
                campaignId, userId);
        return jdbc.update("UPDATE user_entry_quota SET used=used+1 WHERE campaign_id=? AND user_id=? AND used<?",
                campaignId, userId, limit) == 1;
    }

    @Override
    public Entry insert(String id, String campaignId, String userId, String ticketId, Instant submittedAt,
            int wheelSegment, boolean rewardPending) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO entries
                      (id, campaign_id, user_id, ticket_id, submitted_at, wheel_segment, reward_pending)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, id);
            statement.setString(2, campaignId);
            statement.setString(3, userId);
            statement.setString(4, ticketId);
            statement.setTimestamp(5, Timestamp.from(submittedAt));
            statement.setInt(6, wheelSegment);
            statement.setBoolean(7, rewardPending);
            return statement;
        }, keys);
        return new Entry(id, campaignId, userId, ticketId, keys.getKey().longValue(), submittedAt,
                wheelSegment, rewardPending);
    }

    @Override
    public List<Entry> findRewardPendingByCampaign(String campaignId) {
        return jdbc.query("SELECT * FROM entries WHERE campaign_id=? AND reward_pending=TRUE ORDER BY seq",
                ENTRY_ROW, campaignId);
    }

    @Override
    public DrawSnapshot freeze(String campaignId) {
        var existing = findSnapshot(campaignId);
        if (existing.isPresent()) return existing.get();

        List<String> ids = jdbc.queryForList(
                "SELECT id FROM entries WHERE campaign_id=? ORDER BY seq", String.class, campaignId);
        String hash = sha256(String.join("\n", ids));
        var now = Instant.now();
        jdbc.update("INSERT INTO draw_snapshots (campaign_id,total_entries,content_hash,frozen_at) VALUES (?,?,?,?)",
                campaignId, ids.size(), hash, Timestamp.from(now));
        for (int i = 0; i < ids.size(); i++) {
            jdbc.update("INSERT INTO draw_snapshot_items (campaign_id,idx,entry_id) VALUES (?,?,?)",
                    campaignId, i + 1L, ids.get(i));
        }
        return new DrawSnapshot(campaignId, ids.size(), hash, now);
    }

    private Optional<DrawSnapshot> findSnapshot(String campaignId) {
        return one("SELECT * FROM draw_snapshots WHERE campaign_id=?", (rs, n) -> new DrawSnapshot(
                rs.getString("campaign_id"), rs.getLong("total_entries"), rs.getString("content_hash"),
                rs.getTimestamp("frozen_at").toInstant()), campaignId);
    }

    @Override
    public void append(String eventId, String aggregateId, String eventType, Object event) {
        try {
            jdbc.update("INSERT INTO outbox (id,aggregate_id,event_type,payload) VALUES (?,?,?,?)",
                    eventId, aggregateId, eventType, json.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize outbox event", e);
        }
    }

    private <T> Optional<T> one(String sql, RowMapper<T> mapper, Object... args) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, mapper, args));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
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
