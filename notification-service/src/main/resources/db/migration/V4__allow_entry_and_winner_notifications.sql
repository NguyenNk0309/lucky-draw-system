ALTER TABLE notifications
    DROP INDEX uq_notification_entry,
    ADD INDEX idx_notification_entry (entry_id);
