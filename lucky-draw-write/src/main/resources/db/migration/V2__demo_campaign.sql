INSERT INTO campaigns (
    id, seller_id, name, status, max_entries_per_user, start_at, end_at, reward_type, reward_reference
) VALUES (
    'demo-campaign', 'seller-1', 'Demo Loyalty Draw', 'ACTIVE', 2,
    UTC_TIMESTAMP(6) - INTERVAL 1 DAY, UTC_TIMESTAMP(6) + INTERVAL 1 DAY,
    'COUPON', 'WELCOME-50'
);

