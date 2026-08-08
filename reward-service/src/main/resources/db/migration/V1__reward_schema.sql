CREATE TABLE processed_events (
    event_id VARCHAR(36) NOT NULL,
    consumer_name VARCHAR(64) NOT NULL,
    processed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (event_id, consumer_name)
) ENGINE=InnoDB;

CREATE TABLE reward_claims (
    id VARCHAR(36) PRIMARY KEY,
    campaign_id VARCHAR(36) NOT NULL,
    winner_user_id VARCHAR(64) NOT NULL,
    reward_type VARCHAR(16) NOT NULL,
    reference VARCHAR(128) NOT NULL,
    delivery_reference VARCHAR(160),
    delivered_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_claim_campaign (campaign_id),
    KEY idx_claim_user (winner_user_id, created_at)
) ENGINE=InnoDB;

