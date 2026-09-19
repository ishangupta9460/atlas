-- DOM-007: directed Commitment dependency graph. V1–V9 unchanged.
CREATE TABLE commitment_dependency (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    blocking_commitment_id BIGINT NOT NULL,
    blocked_commitment_id BIGINT NOT NULL,
    CONSTRAINT fk_commitment_dependency_blocking FOREIGN KEY (blocking_commitment_id) REFERENCES commitments(id),
    CONSTRAINT fk_commitment_dependency_blocked FOREIGN KEY (blocked_commitment_id) REFERENCES commitments(id),
    CONSTRAINT uk_commitment_dependency_edge UNIQUE (blocking_commitment_id, blocked_commitment_id),
    CONSTRAINT chk_commitment_dependency_not_self CHECK (blocking_commitment_id <> blocked_commitment_id)
);
CREATE INDEX idx_commitment_dependency_blocked ON commitment_dependency(blocked_commitment_id);
