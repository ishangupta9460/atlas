package com.atlas.backend.task;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository queries intentionally limited to the walking-skeleton flow. */
public interface TaskRepository extends JpaRepository<Task, Long> {

    Optional<Task> findByIdAndUserId(Long id, Long userId);

    List<Task> findAllByUserIdAndStatusNot(Long userId, String status);
}
