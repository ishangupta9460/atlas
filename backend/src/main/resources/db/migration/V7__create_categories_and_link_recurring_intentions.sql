-- DOM-005: user-owned Category/Tag configuration and the deferred DOM-004 relationship.
CREATE TABLE categories (
    id                         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id                    BIGINT       NOT NULL,
    name                       VARCHAR(255) NOT NULL,
    default_flexibility_tier   VARCHAR(16)  NOT NULL,
    color                      VARCHAR(255) NOT NULL,
    created_at                 DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_categories_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_categories_flexibility_tier
        CHECK (default_flexibility_tier IN ('fixed', 'protected', 'flexible', 'optional'))
);

CREATE INDEX idx_categories_user_id ON categories(user_id);

-- V6 intentionally permitted any nullable BIGINT here because Category did
-- not yet exist. Such values cannot identify a user-defined Category, so
-- retain the Recurring Intention while clearing only unresolved references
-- before the deferred FK is enforced.
UPDATE recurring_intentions
SET category_id = NULL
WHERE category_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM categories
      WHERE categories.id = recurring_intentions.category_id
  );

ALTER TABLE recurring_intentions
    ADD CONSTRAINT fk_recurring_intentions_category
        FOREIGN KEY (category_id) REFERENCES categories(id);
