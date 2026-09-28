CREATE TABLE goal_risk_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    goal_id BIGINT NOT NULL,
    calculated_at DATETIME(6) NOT NULL,
    remaining_work_estimate DECIMAL(18,6) NOT NULL,
    available_capacity_estimate DECIMAL(18,6) NOT NULL,
    context_adjusted_completion_rate DECIMAL(18,12) NULL,
    confidence DECIMAL(18,12) NULL,
    confidence_threshold DECIMAL(18,12) NOT NULL,
    result VARCHAR(32) NOT NULL,
    triggered_transition BOOLEAN NOT NULL,
    evidence_json TEXT NOT NULL,
    CONSTRAINT fk_risk_goal FOREIGN KEY (goal_id) REFERENCES goals(id),
    CONSTRAINT chk_risk_result CHECK (result IN ('feasible','at_risk','insufficient_evidence')),
    CONSTRAINT chk_risk_threshold CHECK (confidence_threshold > 0 AND confidence_threshold <= 1)
);
CREATE INDEX idx_risk_goal_time ON goal_risk_snapshots(goal_id,calculated_at,id);
CREATE TABLE goal_risk_reviews (
    goal_id BIGINT PRIMARY KEY,
    snapshot_id BIGINT NOT NULL,
    awaiting_response BOOLEAN NOT NULL,
    CONSTRAINT fk_risk_review_goal FOREIGN KEY (goal_id) REFERENCES goals(id),
    CONSTRAINT fk_risk_review_snapshot FOREIGN KEY (snapshot_id) REFERENCES goal_risk_snapshots(id)
);
ALTER TABLE recurring_intentions ADD COLUMN reset_week_start DATE NULL;
