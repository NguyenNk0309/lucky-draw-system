ALTER TABLE reward_claims ADD winner_entry_id VARCHAR(36) NULL AFTER campaign_id;
UPDATE reward_claims SET winner_entry_id=id WHERE winner_entry_id IS NULL;
ALTER TABLE reward_claims
    MODIFY winner_entry_id VARCHAR(36) NOT NULL,
    DROP INDEX uq_claim_campaign,
    ADD UNIQUE KEY uq_claim_entry (winner_entry_id);
