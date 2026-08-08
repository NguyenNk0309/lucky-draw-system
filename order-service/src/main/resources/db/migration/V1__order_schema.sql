CREATE TABLE orders (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    total DECIMAL(14,2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT chk_order_total CHECK (total > 0)
) ENGINE=InnoDB;

CREATE TABLE order_outbox (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at DATETIME(6),
    KEY idx_order_outbox_unpublished (published_at, created_at)
) ENGINE=InnoDB;

