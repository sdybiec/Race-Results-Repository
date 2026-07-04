-- Race Results Repository - RML document type + model namespace metadata (H2)
-- Version 2.0.0

-- Self-describing metamodel namespace for every document (e.g. the TDI or RML nsURI).
ALTER TABLE documents ADD COLUMN model_ns_uri VARCHAR(255);

-- Backfill: all existing documents are TDI (start lists and race results).
UPDATE documents SET model_ns_uri = 'http://www.rowtown.org/TDI/1.0.0' WHERE model_ns_uri IS NULL;

-- Access control for the new RML (Regatta Definition) document type.
INSERT INTO acl_entries (regattaId, resource_type, operation, role, allowed) VALUES
('*', 'RML', 'CREATE', 'REGATTA_ADMIN', TRUE),
('*', 'RML', 'READ', 'REGATTA_ADMIN', TRUE),
('*', 'RML', 'UPDATE', 'REGATTA_ADMIN', TRUE),
('*', 'RML', 'DELETE', 'REGATTA_ADMIN', TRUE),
('*', 'RML', 'ROLLBACK', 'REGATTA_ADMIN', TRUE),
('*', 'RML', 'SUBSCRIBE', 'REGATTA_ADMIN', TRUE),
('*', 'RML', 'CREATE', 'TIMER', TRUE),
('*', 'RML', 'READ', 'TIMER', TRUE),
('*', 'RML', 'UPDATE', 'TIMER', TRUE),
('*', 'RML', 'DELETE', 'TIMER', TRUE),
('*', 'RML', 'ROLLBACK', 'TIMER', TRUE),
('*', 'RML', 'SUBSCRIBE', 'TIMER', TRUE),
('*', 'RML', 'READ', 'VIEWER', TRUE),
('*', 'RML', 'SUBSCRIBE', 'VIEWER', TRUE);
