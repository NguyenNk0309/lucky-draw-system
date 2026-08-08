CREATE TABLE campaigns (
    id VARCHAR(36) PRIMARY KEY,
    seller_id VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    status VARCHAR(16) NOT NULL,
    max_entries_per_user INT NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    reward_type VARCHAR(16) NOT NULL,
    reward_reference VARCHAR(128) NOT NULL,
    winner_entry_id VARCHAR(36),
    snapshot_hash CHAR(64),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_campaign_status CHECK (status IN ('DRAFT','ACTIVE','ENDED','DRAWN','CANCELLED')),
    CONSTRAINT chk_campaign_reward CHECK (reward_type IN ('PRODUCT','COUPON')),
    CONSTRAINT chk_campaign_limit CHECK (max_entries_per_user > 0),
    CONSTRAINT chk_campaign_window CHECK (start_at < end_at),
    KEY idx_campaign_status_end (status, end_at),
    KEY idx_campaign_seller (seller_id)
) ENGINE=InnoDB;

CREATE TABLE tickets (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ISSUED',
    campaign_id VARCHAR(36),
    consumed_by_entry_id VARCHAR(36),
    issued_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    consumed_at DATETIME(6),
    CONSTRAINT chk_ticket_status CHECK (status IN ('ISSUED','CONSUMED')),
    UNIQUE KEY uq_ticket_order (order_id),
    KEY idx_ticket_user_status (user_id, status)
) ENGINE=InnoDB;

CREATE TABLE user_entry_quota (
    campaign_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    used INT NOT NULL DEFAULT 0,
    PRIMARY KEY (campaign_id, user_id),
    CONSTRAINT chk_quota_used CHECK (used >= 0)
) ENGINE=InnoDB;

CREATE TABLE entries (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    campaign_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    ticket_id VARCHAR(36) NOT NULL,
    seq BIGINT NOT NULL AUTO_INCREMENT,
    submitted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_entry_seq (seq),
    UNIQUE KEY uq_entry_ticket (ticket_id),
    KEY idx_entry_campaign_seq (campaign_id, seq),
    KEY idx_entry_campaign_user (campaign_id, user_id)
) ENGINE=InnoDB;

CREATE TABLE draw_snapshots (
    campaign_id VARCHAR(36) PRIMARY KEY,
    total_entries BIGINT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    frozen_at DATETIME(6) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE draw_snapshot_items (
    campaign_id VARCHAR(36) NOT NULL,
    idx BIGINT NOT NULL,
    entry_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (campaign_id, idx),
    UNIQUE KEY uq_snapshot_entry (campaign_id, entry_id)
) ENGINE=InnoDB;

CREATE TABLE draw_audit (
    campaign_id VARCHAR(36) PRIMARY KEY,
    selected_index BIGINT NOT NULL,
    winner_entry_id VARCHAR(36) NOT NULL,
    snapshot_hash CHAR(64) NOT NULL,
    drawn_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

CREATE TABLE outbox (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at DATETIME(6),
    KEY idx_outbox_unpublished (published_at, created_at)
) ENGINE=InnoDB;

