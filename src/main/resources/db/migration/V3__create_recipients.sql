CREATE TABLE recipients (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT NOT NULL,
    campaign_id BIGINT NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    email       VARCHAR(320),
    phone       VARCHAR(30),
    push_token  VARCHAR(512),
    timezone    VARCHAR(64) NOT NULL DEFAULT 'UTC',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_recipient_tenant   FOREIGN KEY (tenant_id)   REFERENCES tenants(id),
    CONSTRAINT fk_recipient_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(id),
    INDEX idx_recipient_tenant   (tenant_id),
    INDEX idx_recipient_campaign (campaign_id)
);
