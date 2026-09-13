package com.atlas.backend.recurringintention;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringIntentionRepository extends JpaRepository<RecurringIntention, Long> {
    Optional<RecurringIntention> findByIdAndUserId(Long id, Long userId);
}
