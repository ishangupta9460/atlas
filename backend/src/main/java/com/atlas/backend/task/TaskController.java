package com.atlas.backend.task;

import com.atlas.backend.user.User;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Protected task endpoints for the minimal FOUND-003 walking skeleton. */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(taskService.create(currentUser.getId(), request));
    }

    @GetMapping("/today")
    public ResponseEntity<List<TaskResponse>> today(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(taskService.today(currentUser.getId()));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<TaskResponse> start(
            @AuthenticationPrincipal User currentUser, @PathVariable Long id) {
        return ResponseEntity.ok(taskService.start(currentUser.getId(), id));
    }

    @PostMapping("/{id}/finish")
    public ResponseEntity<TaskResponse> finish(
            @AuthenticationPrincipal User currentUser, @PathVariable Long id) {
        return ResponseEntity.ok(taskService.finish(currentUser.getId(), id));
    }
}
