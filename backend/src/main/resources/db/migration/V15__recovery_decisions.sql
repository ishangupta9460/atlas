CREATE TABLE recovery_decisions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tier VARCHAR(20) NOT NULL,
    state VARCHAR(20) NOT NULL,
    request_json TEXT NOT NULL,
    proposal_json TEXT NOT NULL,
    reason TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_recovery_owner FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_recovery_tier CHECK (tier IN ('AUTONOMOUS','COLLABORATIVE','CRITICAL')),
    CONSTRAINT chk_recovery_state CHECK (state IN ('pending','applied','dismissed'))
);
CREATE INDEX idx_recovery_owner_state ON recovery_decisions(user_id,state,id);

CREATE TABLE recovery_block_claims (
    scheduled_block_id BIGINT PRIMARY KEY,
    decision_id BIGINT NOT NULL,
    CONSTRAINT fk_recovery_claim_block FOREIGN KEY (scheduled_block_id) REFERENCES scheduled_blocks(id),
    CONSTRAINT fk_recovery_claim_decision FOREIGN KEY (decision_id) REFERENCES recovery_decisions(id)
);
