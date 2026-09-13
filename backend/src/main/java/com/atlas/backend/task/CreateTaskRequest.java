package com.atlas.backend.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for creating a minimal walking-skeleton task. */
public record CreateTaskRequest(
        @NotBlank(message = "title must not be blank")
        @Size(max = 255, message = "title must be at most 255 characters")
        String title) {
}
