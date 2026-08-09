ALTER TABLE entries
    ADD reward_cancelled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD reward_cancelled_at DATETIME(6),
    ADD CONSTRAINT chk_reward_cancelled CHECK (reward_cancelled=FALSE OR reward_pending=TRUE),
    DROP INDEX idx_entry_pending_reward,
    ADD INDEX idx_entry_pending_reward (campaign_id, reward_pending, reward_cancelled);
