-- Race Results Repository - Initial Database Schema (H2)
-- Version 1.0.0
--
-- Native H2 DDL (no MySQL compatibility mode required):
--   * BLOB instead of LONGBLOB
--   * VARCHAR instead of TEXT/JSON
--   * indexes created as separate CREATE INDEX statements (H2 does not support
--     MySQL-style inline INDEX definitions)
--   * no ENGINE / CHARSET / COLLATE clauses

-- Documents table
CREATE TABLE documents (
    document_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    documentType VARCHAR(50) NOT NULL,
    regattaId VARCHAR(255) NOT NULL,
    regatta_start_date DATE NOT NULL,
    timerId VARCHAR(255),
    milestoneId VARCHAR(255),
    versionType VARCHAR(50),
    author VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    latest_version BIGINT NOT NULL DEFAULT 1,
    description VARCHAR(2000)
);
CREATE INDEX idx_documents_regatta ON documents(regattaId);
CREATE INDEX idx_documents_timer ON documents(timerId);
CREATE INDEX idx_documents_type ON documents(documentType);

-- Versions table
CREATE TABLE versions (
    version_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    version_number BIGINT NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    author VARCHAR(255) NOT NULL,
    change_description VARCHAR(2000),
    model_snapshot BLOB NOT NULL,
    snapshot_format VARCHAR(10) NOT NULL,
    checksum VARCHAR(64),
    CONSTRAINT uk_document_version UNIQUE (document_id, version_number),
    CONSTRAINT fk_versions_document FOREIGN KEY (document_id)
        REFERENCES documents(document_id) ON DELETE CASCADE
);
CREATE INDEX idx_timestamp ON versions(timestamp);

-- Metadata table
CREATE TABLE metadata (
    metadata_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    meta_key VARCHAR(255) NOT NULL,
    meta_value VARCHAR(4000),
    CONSTRAINT fk_metadata_document FOREIGN KEY (document_id)
        REFERENCES documents(document_id) ON DELETE CASCADE
);
CREATE INDEX idx_key_value ON metadata(meta_key, meta_value);

-- Tags table
CREATE TABLE tags (
    tag_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    tag_name VARCHAR(100) NOT NULL,
    CONSTRAINT fk_tags_document FOREIGN KEY (document_id)
        REFERENCES documents(document_id) ON DELETE CASCADE
);
CREATE INDEX idx_tag ON tags(tag_name);

-- ACL entries table
CREATE TABLE acl_entries (
    acl_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    regattaId VARCHAR(255) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    operation VARCHAR(50) NOT NULL,
    role VARCHAR(50) NOT NULL,
    allowed BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_acl_regatta ON acl_entries(regattaId);

-- Document ownership table
CREATE TABLE document_ownership (
    ownership_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    owner_user_id VARCHAR(255) NOT NULL,
    CONSTRAINT fk_ownership_document FOREIGN KEY (document_id)
        REFERENCES documents(document_id) ON DELETE CASCADE
);
CREATE INDEX idx_owner ON document_ownership(owner_user_id);

-- Webhook subscriptions table
CREATE TABLE webhook_subscriptions (
    subscription_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT,
    regattaId VARCHAR(255),
    webhook_url VARCHAR(1024) NOT NULL,
    secret_key VARCHAR(255) NOT NULL,
    events VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    failed_deliveries INT NOT NULL DEFAULT 0,
    last_failure TIMESTAMP NULL
);
CREATE INDEX idx_webhooks_document ON webhook_subscriptions(document_id);
CREATE INDEX idx_webhooks_regatta ON webhook_subscriptions(regattaId);

-- MQTT subscriptions table
CREATE TABLE mqtt_subscriptions (
    subscription_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    topic_pattern VARCHAR(512) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_user ON mqtt_subscriptions(user_id);

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
