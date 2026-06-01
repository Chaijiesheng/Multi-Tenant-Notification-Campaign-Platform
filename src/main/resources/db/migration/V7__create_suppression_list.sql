CREATE TABLE suppression_list (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id            BIGINT NOT NULL,
    recipient_external_id VARCHAR(255) NOT NULL,
    channel              ENUM('EMAIL','SMS','PUSH') NOT NULL,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_suppression_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT uq_suppression UNIQUE (tenant_id, recipient_external_id, channel),
    INDEX idx_suppression_lookup (tenant_id, recipient_external_id, channel)
);

INSERT INTO suppression_list (tenant_id, recipient_external_id, channel)
VALUES (1, 'suppressed-user-001', 'SMS'),
       (1, 'suppressed-user-002', 'EMAIL');
