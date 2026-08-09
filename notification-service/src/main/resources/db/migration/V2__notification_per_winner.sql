ALTER TABLE notifications
    DROP INDEX uq_notification_campaign,
    ADD UNIQUE KEY uq_notification_user_campaign (campaign_id, user_id);
