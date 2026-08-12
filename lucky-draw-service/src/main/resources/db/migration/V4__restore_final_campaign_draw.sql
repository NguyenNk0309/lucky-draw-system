ALTER TABLE entries
    DROP INDEX idx_entry_pending_reward,
    DROP CHECK chk_reward_cancelled,
    DROP CHECK chk_wheel_segment,
    DROP COLUMN reward_cancelled_at,
    DROP COLUMN reward_cancelled,
    DROP COLUMN reward_pending,
    DROP COLUMN wheel_segment;
