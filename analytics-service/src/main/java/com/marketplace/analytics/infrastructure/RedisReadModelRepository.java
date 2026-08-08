package com.marketplace.analytics.infrastructure;

import com.marketplace.analytics.domain.CampaignStats;
import com.marketplace.analytics.domain.MyResult;
import com.marketplace.analytics.domain.port.ReadModelRepository;
import com.marketplace.events.CampaignUpdated;
import com.marketplace.events.EntrySubmitted;
import com.marketplace.events.WinnerPicked;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RedisReadModelRepository implements ReadModelRepository {
    private static final DefaultRedisScript<Long> CAMPAIGN = script("""
            if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
            redis.call('SET', KEYS[1], '1')
            redis.call('HSET', KEYS[2], 'sellerId', ARGV[1], 'name', ARGV[2], 'status', ARGV[3],
              'maxEntries', ARGV[4], 'startAt', ARGV[5], 'endAt', ARGV[6], 'lastUpdatedAt', ARGV[7])
            return 1
            """);
    private static final DefaultRedisScript<Long> ENTRY = script("""
            if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
            redis.call('SET', KEYS[1], '1')
            redis.call('INCR', KEYS[2])
            redis.call('SADD', KEYS[3], ARGV[1])
            redis.call('RPUSH', KEYS[4], ARGV[2])
            redis.call('HSET', KEYS[5], 'maxEntries', ARGV[3], 'lastUpdatedAt', ARGV[4])
            return 1
            """);
    private static final DefaultRedisScript<Long> WINNER = script("""
            if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
            redis.call('SET', KEYS[1], '1')
            redis.call('HSET', KEYS[2], 'winnerEntryId', ARGV[1], 'winnerUserId', ARGV[2],
              'snapshotHash', ARGV[3], 'status', 'DRAWN', 'lastUpdatedAt', ARGV[4])
            return 1
            """);

    private final StringRedisTemplate redis;

    public RedisReadModelRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean project(CampaignUpdated event) {
        return execute(CAMPAIGN, List.of(processed(event.eventId().toString()), meta(event.campaignId())),
                event.sellerId(), event.name(), event.status(), Integer.toString(event.maxEntriesPerUser()),
                event.startAt().toString(), event.endAt().toString(), event.occurredAt().toString());
    }

    @Override
    public boolean project(EntrySubmitted event) {
        String root = root(event.campaignId());
        return execute(ENTRY, List.of(processed(event.eventId().toString()), root + ":total",
                        root + ":participants", root + ":user:" + event.userId() + ":entries", meta(event.campaignId())),
                event.userId(), event.entryId(), Integer.toString(event.maxEntriesPerUser()), event.occurredAt().toString());
    }

    @Override
    public boolean project(WinnerPicked event) {
        return execute(WINNER, List.of(processed(event.eventId().toString()), meta(event.campaignId())),
                event.winnerEntryId(), event.winnerUserId(), event.snapshotHash(), event.occurredAt().toString());
    }

    @Override
    public Optional<String> sellerId(String campaignId) {
        return Optional.ofNullable(redis.opsForHash().get(meta(campaignId), "sellerId")).map(Object::toString);
    }

    @Override
    public CampaignStats stats(String campaignId) {
        Map<Object, Object> values = redis.opsForHash().entries(meta(campaignId));
        return new CampaignStats(campaignId, string(values, "name"), string(values, "status"),
                number(redis.opsForValue().get(root(campaignId) + ":total")),
                Optional.ofNullable(redis.opsForSet().size(root(campaignId) + ":participants")).orElse(0L),
                string(values, "winnerEntryId"), string(values, "winnerUserId"), string(values, "snapshotHash"),
                instant(values, "lastUpdatedAt"));
    }

    @Override
    public MyResult mine(String campaignId, String userId) {
        Map<Object, Object> values = redis.opsForHash().entries(meta(campaignId));
        List<String> entries = redis.opsForList().range(root(campaignId) + ":user:" + userId + ":entries", 0, -1);
        if (entries == null) entries = List.of();
        int maximum = (int) number(string(values, "maxEntries"));
        String winnerUser = string(values, "winnerUserId");
        return new MyResult(campaignId, entries, Math.max(0, maximum - entries.size()),
                string(values, "winnerEntryId"), userId.equals(winnerUser), instant(values, "lastUpdatedAt"));
    }

    private boolean execute(DefaultRedisScript<Long> script, List<String> keys, String... args) {
        return Long.valueOf(1).equals(redis.execute(script, keys, (Object[]) args));
    }

    private static String root(String id) { return "campaign:" + id; }
    private static String meta(String id) { return root(id) + ":meta"; }
    private static String processed(String id) { return "analytics:processed:" + id; }
    private static String string(Map<Object, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }
    private static long number(String value) { return value == null ? 0 : Long.parseLong(value); }
    private static Instant instant(Map<Object, Object> map, String key) {
        String value = string(map, key);
        return value == null ? null : Instant.parse(value);
    }
    private static DefaultRedisScript<Long> script(String text) {
        return new DefaultRedisScript<>(text, Long.class);
    }
}

