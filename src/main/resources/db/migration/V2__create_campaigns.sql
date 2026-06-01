CREATE TABLE campaigns (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id        BIGINT NOT NULL,
    name             VARCHAR(255) NOT NULL,
    channel          ENUM('EMAIL','SMS','PUSH') NOT NULL,
    message_body     TEXT NOT NULL,
    status           ENUM('DRAFT','PROCESSING','COMPLETED','PARTIAL_FAILURE') NOT NULL DEFAULT 'DRAFT',
    total_recipients INT NOT NULL DEFAULT 0,
    sent_count       INT NOT NULL DEFAULT 0,
    failed_count     INT NOT NULL DEFAULT 0,
    skipped_count    INT NOT NULL DEFAULT 0,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_campaign_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    INDEX idx_campaign_tenant_status (tenant_id, status)
);
