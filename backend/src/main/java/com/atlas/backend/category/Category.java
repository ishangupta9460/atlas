package com.atlas.backend.category;

import jakarta.persistence.*;
import java.time.Instant;

/** User-defined, flat category configuration; it has no independent state machine. */
@Entity
@Table(name = "categories")
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, length = 255)
    private String name;
    @Column(name = "default_flexibility_tier", nullable = false, length = 16)
    private String defaultFlexibilityTier;
    @Column(name = "default_importance", length = 16)
    private String defaultImportance;
    @Column(nullable = false, length = 255)
    private String color;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist private void prePersist() { if (createdAt == null) createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getDefaultFlexibilityTier() { return defaultFlexibilityTier; }
    public String getDefaultImportance() { return defaultImportance; }
    void setDefaultImportance(String value) { defaultImportance = value; }
    public String getColor() { return color; }
    public Instant getCreatedAt() { return createdAt; }

    void update(String name, String defaultFlexibilityTier, String color) {
        if (name != null) this.name = name;
        if (defaultFlexibilityTier != null) this.defaultFlexibilityTier = defaultFlexibilityTier;
        if (color != null) this.color = color;
    }

    static Category create(Long userId, String name, String defaultFlexibilityTier, String color) {
        Category category = new Category();
        category.userId = userId;
        category.name = name;
        category.defaultFlexibilityTier = defaultFlexibilityTier;
        category.color = color;
        return category;
    }
}
