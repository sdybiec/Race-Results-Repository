-- Race Results Repository - Initial Database Schema
-- Version 1.0.0

-- Documents table
CREATE TABLE documents (
    document_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    documentType VARCHAR(50) NOT NULL,
    regattaId VARCHAR(255) NOT NULL,
    timerId VARCHAR(255),
    milestoneId VARCHAR(255),
    versionType VARCHAR(50),
    author VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    latest_version BIGINT NOT NULL DEFAULT 1,
    description VARCHAR(2000),
    INDEX idx_documents_regatta (regattaId),
    INDEX idx_documents_timer (timerId),
    INDEX idx_documents_type (documentType)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Versions table
CREATE TABLE versions (
    version_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    version_number BIGINT NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    author VARCHAR(255) NOT NULL,
    change_description VARCHAR(2000),
    model_snapshot LONGBLOB NOT NULL,
    snapshot_format VARCHAR(10) NOT NULL,
    checksum VARCHAR(64),
    FOREIGN KEY (document_id) REFERENCES documents(document_id) ON DELETE CASCADE,
    UNIQUE KEY uk_document_version (document_id, version_number),
    INDEX idx_timestamp (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Metadata table
CREATE TABLE metadata (
    metadata_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    meta_key VARCHAR(255) NOT NULL,
    meta_value TEXT,
    FOREIGN KEY (document_id) REFERENCES documents(document_id) ON DELETE CASCADE,
    INDEX idx_key_value (meta_key, meta_value(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tags table
CREATE TABLE tags (
    tag_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    tag_name VARCHAR(100) NOT NULL,
    FOREIGN KEY (document_id) REFERENCES documents(document_id) ON DELETE CASCADE,
    INDEX idx_tag (tag_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ACL entries table
CREATE TABLE acl_entries (
    acl_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    regattaId VARCHAR(255) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    operation VARCHAR(50) NOT NULL,
    role VARCHAR(50) NOT NULL,
    allowed BOOLEAN NOT NULL DEFAULT TRUE,
    INDEX idx_acl_regatta (regattaId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Document ownership table
CREATE TABLE document_ownership (
    ownership_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    owner_user_id VARCHAR(255) NOT NULL,
    FOREIGN KEY (document_id) REFERENCES documents(document_id) ON DELETE CASCADE,
    INDEX idx_owner (owner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Webhook subscriptions table
CREATE TABLE webhook_subscriptions (
    subscription_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT,
    regattaId VARCHAR(255),
    webhook_url VARCHAR(1024) NOT NULL,
    secret_key VARCHAR(255) NOT NULL,
    events JSON,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    failed_deliveries INT NOT NULL DEFAULT 0,
    last_failure TIMESTAMP,
    INDEX idx_webhooks_document (document_id),
    INDEX idx_webhooks_regatta (regattaId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- MQTT subscriptions table
CREATE TABLE mqtt_subscriptions (
    subscription_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(255) NOT NULL,
    topic_pattern VARCHAR(512) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Full-text search index on description field (MariaDB only)
-- Note: H2 does not support FULLTEXT indexes, but regular index will work
-- ALTER TABLE documents ADD FULLTEXT INDEX ft_description (description);

-- Insert default ACL entries for standard permissions
INSERT INTO acl_entries (regattaId, resource_type, operation, role, allowed) VALUES
-- Regatta Admin permissions
('*', 'START_LIST', 'CREATE', 'REGATTA_ADMIN', TRUE),
('*', 'START_LIST', 'READ', 'REGATTA_ADMIN', TRUE),
('*', 'START_LIST', 'UPDATE', 'REGATTA_ADMIN', TRUE),
('*', 'START_LIST', 'DELETE', 'REGATTA_ADMIN', TRUE),
('*', 'START_LIST', 'ROLLBACK', 'REGATTA_ADMIN', TRUE),
('*', 'START_LIST', 'SUBSCRIBE', 'REGATTA_ADMIN', TRUE),
('*', 'RACE_RESULTS', 'CREATE', 'REGATTA_ADMIN', TRUE),
('*', 'RACE_RESULTS', 'READ', 'REGATTA_ADMIN', TRUE),
('*', 'RACE_RESULTS', 'UPDATE', 'REGATTA_ADMIN', TRUE),
('*', 'RACE_RESULTS', 'DELETE', 'REGATTA_ADMIN', TRUE),
('*', 'RACE_RESULTS', 'ROLLBACK', 'REGATTA_ADMIN', TRUE),
('*', 'RACE_RESULTS', 'SUBSCRIBE', 'REGATTA_ADMIN', TRUE),

-- Timer permissions
('*', 'START_LIST', 'READ', 'TIMER', TRUE),
('*', 'START_LIST', 'SUBSCRIBE', 'TIMER', TRUE),
('*', 'RACE_RESULTS', 'CREATE', 'TIMER', TRUE),
('*', 'RACE_RESULTS', 'READ', 'TIMER', TRUE),
('*', 'RACE_RESULTS', 'UPDATE', 'TIMER', TRUE),
('*', 'RACE_RESULTS', 'ROLLBACK', 'TIMER', TRUE),
('*', 'RACE_RESULTS', 'SUBSCRIBE', 'TIMER', TRUE),

-- Viewer permissions
('*', 'START_LIST', 'READ', 'VIEWER', TRUE),
('*', 'START_LIST', 'SUBSCRIBE', 'VIEWER', TRUE),
('*', 'RACE_RESULTS', 'READ', 'VIEWER', TRUE),
('*', 'RACE_RESULTS', 'SUBSCRIBE', 'VIEWER', TRUE);
