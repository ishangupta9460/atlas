package com.atlas.backend.roadmap;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateMilestoneRequest {
    @Size(max = 255, message = "title must be at most 255 characters")
    @Pattern(regexp = ".*\\S.*", message = "title must not be blank")
    private String title;
    private Integer order;

    public String getTitle() { return title; }
    public Integer getOrder() { return order; }
    public void setTitle(String title) { this.title = title; }
    public void setOrder(Integer order) { this.order = order; }
}
