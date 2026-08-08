CREATE TABLE processed_events (
    event_id VARCHAR(36) NOT NULL,
    consumer_name VARCHAR(64) NOT NULL,
    processed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (event_id, consumer_name)
) ENGINE=InnoDB;

CREATE TABLE notifications (
    id VARCHAR(36) PRIMARY KEY,
    campaign_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    message VARCHAR(255) NOT NULL,
    sent_at DATETIME(6) NOT NULL,
    UNIQUE KEY uq_notification_campaign (campaign_id),
    KEY idx_notification_user (user_id, sent_at)
) ENGINE=InnoDB;

