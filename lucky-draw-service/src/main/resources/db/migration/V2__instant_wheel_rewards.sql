ALTER TABLE entries
    ADD wheel_segment TINYINT NOT NULL DEFAULT 0,
    ADD reward_pending BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT chk_wheel_segment CHECK (wheel_segment BETWEEN 0 AND 7);

CREATE INDEX idx_entry_pending_reward ON entries (campaign_id, reward_pending);
