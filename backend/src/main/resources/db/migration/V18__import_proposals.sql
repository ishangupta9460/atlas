-- Import review/replay payloads can exceed MySQL TEXT's 64 KiB bound.
-- Widening preserves existing keys and response contents.
ALTER TABLE execution_idempotency MODIFY COLUMN fingerprint MEDIUMTEXT NOT NULL;
ALTER TABLE execution_idempotency MODIFY COLUMN response_json MEDIUMTEXT NOT NULL;
CREATE TABLE import_proposals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    media_type VARCHAR(100) NOT NULL,
    upload_bytes MEDIUMBLOB NOT NULL,
    proposal_json MEDIUMTEXT NOT NULL,
    result_json MEDIUMTEXT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'review',
    revision INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_import_owner FOREIGN KEY(user_id) REFERENCES users(id),
    CONSTRAINT chk_import_kind CHECK(kind IN ('roadmap','fixed')),
    CONSTRAINT chk_import_state CHECK(state IN ('review','approved'))
);
CREATE INDEX idx_import_owner ON import_proposals(user_id,id);
