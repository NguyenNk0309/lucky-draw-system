INSERT INTO orders (id, user_id, total, created_at) VALUES
('demo-order-qualifying-1', 'customer-1', 1200000.00, NOW(6)),
('demo-order-qualifying-2', 'customer-1', 1500000.00, NOW(6)),
('demo-order-nonqualifying', 'customer-1', 500000.00, NOW(6));

INSERT INTO order_outbox (id, aggregate_id, event_type, payload) VALUES
('10000000-0000-0000-0000-000000000001', 'demo-order-qualifying-1', 'OrderCompleted',
 JSON_OBJECT('eventId','10000000-0000-0000-0000-000000000001','occurredAt',DATE_FORMAT(UTC_TIMESTAMP(6),'%Y-%m-%dT%H:%i:%s.%fZ'),'aggregateId','demo-order-qualifying-1','correlationId','demo-seed-1','orderId','demo-order-qualifying-1','userId','customer-1','total',1200000.00)),
('10000000-0000-0000-0000-000000000002', 'demo-order-qualifying-2', 'OrderCompleted',
 JSON_OBJECT('eventId','10000000-0000-0000-0000-000000000002','occurredAt',DATE_FORMAT(UTC_TIMESTAMP(6),'%Y-%m-%dT%H:%i:%s.%fZ'),'aggregateId','demo-order-qualifying-2','correlationId','demo-seed-2','orderId','demo-order-qualifying-2','userId','customer-1','total',1500000.00));

