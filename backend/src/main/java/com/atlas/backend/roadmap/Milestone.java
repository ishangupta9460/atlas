package com.atlas.backend.roadmap;

import jakarta.persistence.*;

/** Non-schedulable grouping unit within a Roadmap. */
@Entity
@Table(name = "milestones")
public class Milestone {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "roadmap_id", nullable = false)
    private Long roadmapId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    public Long getId() { return id; }
    public Long getRoadmapId() { return roadmapId; }
    public String getTitle() { return title; }
    public Integer getDisplayOrder() { return displayOrder; }

    void update(String title, Integer displayOrder) {
        if (title != null) this.title = title;
        if (displayOrder != null) this.displayOrder = displayOrder;
    }

    static Milestone create(Long roadmapId, String title, Integer displayOrder) {
        Milestone milestone = new Milestone();
        milestone.roadmapId = roadmapId;
        milestone.title = title;
        milestone.displayOrder = displayOrder;
        return milestone;
    }
}
