ALTER TABLE notifications ADD entry_id VARCHAR(36) NULL AFTER campaign_id;
UPDATE notifications SET entry_id=id WHERE entry_id IS NULL;
ALTER TABLE notifications
    MODIFY entry_id VARCHAR(36) NOT NULL,
    DROP INDEX uq_notification_user_campaign,
    ADD UNIQUE KEY uq_notification_entry (entry_id);
