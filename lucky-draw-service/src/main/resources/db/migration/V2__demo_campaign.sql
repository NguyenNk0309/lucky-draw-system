INSERT INTO campaigns (
    id, seller_id, name, status, max_entries_per_user, start_at, end_at, reward_type, reward_reference
) VALUES (
    'demo-campaign', 'seller-1', 'Demo Loyalty Draw', 'ACTIVE', 2,
    UTC_TIMESTAMP(6) - INTERVAL 1 DAY, UTC_TIMESTAMP(6) + INTERVAL 1 DAY,
    'COUPON', 'WELCOME-50'
);

INSERT INTO outbox (id, aggregate_id, event_type, payload)
SELECT '20000000-0000-0000-0000-000000000001', id, 'CampaignUpdated',
 JSON_OBJECT(
   'eventId','20000000-0000-0000-0000-000000000001',
   'occurredAt',DATE_FORMAT(UTC_TIMESTAMP(6),'%Y-%m-%dT%H:%i:%s.%fZ'),
   'aggregateId',id,'correlationId','demo-campaign-seed','campaignId',id,
   'sellerId',seller_id,'name',name,'status',status,'maxEntriesPerUser',max_entries_per_user,
   'startAt',DATE_FORMAT(start_at,'%Y-%m-%dT%H:%i:%s.%fZ'),
   'endAt',DATE_FORMAT(end_at,'%Y-%m-%dT%H:%i:%s.%fZ'),
   'reward',JSON_OBJECT('type',reward_type,'reference',reward_reference)
 )
FROM campaigns WHERE id='demo-campaign';
