package com.atlas.backend.task;

import com.atlas.backend.event.Event;
import com.atlas.backend.event.EventRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns all FOUND-003 task transitions and their paired event writes.
 *
 * <p>Each mutation is transactional so failure of either the task write or
 * event write rolls back the complete state change.
 */
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final EventRepository eventRepository;

    public TaskService(TaskRepository taskRepository, EventRepository eventRepository) {
        this.taskRepository = taskRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public TaskResponse create(Long userId, CreateTaskRequest request) {
        Task task = taskRepository.save(Task.ready(userId, request.title()));
        eventRepository.save(Event.of(task.getId(), "task.created"));
        return TaskResponse.from(task);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> today(Long userId) {
        return taskRepository.findAllByUserIdAndStatusNot(userId, Task.COMPLETED).stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional
    public TaskResponse start(Long userId, Long taskId) {
        Task task = findOwnedTask(userId, taskId);
        if (!Task.READY.equals(task.getStatus())) {
            throw new InvalidTaskStateException("Only ready tasks can be started");
        }

        task.start(Instant.now());
        eventRepository.save(Event.of(task.getId(), "task.started"));
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse finish(Long userId, Long taskId) {
        Task task = findOwnedTask(userId, taskId);
        if (!Task.IN_PROGRESS.equals(task.getStatus())) {
            throw new InvalidTaskStateException("Only in-progress tasks can be finished");
        }

        task.finish(Instant.now());
        eventRepository.save(Event.of(task.getId(), "task.finished"));
        return TaskResponse.from(task);
    }

    private Task findOwnedTask(Long userId, Long taskId) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(TaskNotFoundException::new);
    }
}
