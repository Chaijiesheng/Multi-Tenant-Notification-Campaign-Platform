ALTER TABLE campaigns ADD COLUMN is_transactional TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE campaigns ADD COLUMN message_body_hash VARCHAR(64) NULL;
