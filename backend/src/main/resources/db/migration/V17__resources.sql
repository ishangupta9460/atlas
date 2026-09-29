-- Owner-composite keys prevent cross-tenant attachments even outside the API.
ALTER TABLE commitments ADD CONSTRAINT uq_commitment_owner UNIQUE (id, user_id);
CREATE TABLE resources (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(16) NOT NULL,
    title VARCHAR(255) NOT NULL,
    url_or_file_ref VARCHAR(2048) NOT NULL,
    added_by VARCHAR(16) NOT NULL DEFAULT 'user',
    CONSTRAINT fk_resource_owner FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uq_resource_owner UNIQUE (id, user_id),
    CONSTRAINT chk_resource_type CHECK (type IN ('video','pdf','doc','link','book','course')),
    CONSTRAINT chk_resource_added CHECK (added_by IN ('user','ai_suggested'))
);
CREATE INDEX idx_resources_owner ON resources(user_id,id);
CREATE TABLE task_resource (
    user_id BIGINT NOT NULL,
    commitment_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    PRIMARY KEY (commitment_id,resource_id),
    CONSTRAINT fk_attachment_task FOREIGN KEY (commitment_id,user_id) REFERENCES commitments(id,user_id),
    CONSTRAINT fk_attachment_resource FOREIGN KEY (resource_id,user_id) REFERENCES resources(id,user_id)
);
CREATE INDEX idx_attachment_resource ON task_resource(resource_id,user_id);
CREATE TABLE resource_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    tier VARCHAR(32) NOT NULL DEFAULT 'single_reaction',
    reaction VARCHAR(16) NOT NULL,
    comment TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_feedback_resource FOREIGN KEY (resource_id,user_id) REFERENCES resources(id,user_id),
    CONSTRAINT chk_feedback_tier CHECK (tier='single_reaction'),
    CONSTRAINT chk_feedback_reaction CHECK (reaction IN ('liked','disliked'))
);
CREATE INDEX idx_feedback_owner ON resource_feedback(user_id,created_at,id);
