package com.atlas.backend.goal;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** PATCH payload; a missing deadline differs from an explicit JSON null. */
public class UpdateGoalRequest {
    @Size(max = 255, message = "title must be at most 255 characters")
    @Pattern(regexp = ".*\\S.*", message = "title must not be blank")
    private String title;
    private LocalDate targetDeadline;
    private boolean targetDeadlineProvided;

    public String getTitle() { return title; }
    public LocalDate getTargetDeadline() { return targetDeadline; }
    public boolean isTargetDeadlineProvided() { return targetDeadlineProvided; }

    public void setTitle(String title) { this.title = title; }

    @JsonSetter(value = "targetDeadline", nulls = Nulls.SET)
    public void setTargetDeadline(LocalDate targetDeadline) {
        this.targetDeadline = targetDeadline;
        this.targetDeadlineProvided = true;
    }
}
