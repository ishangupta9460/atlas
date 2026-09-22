package com.atlas.backend.commitment;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CommitmentRepository extends JpaRepository<Commitment, Long> {
    java.util.List<Commitment> findByUserIdAndGoalIdAndIdLessThanOrderByIdDesc(
            Long userId, Long goalId, Long cursor, org.springframework.data.domain.Pageable pageable);
    Optional<Commitment> findByIdAndUserId(Long id, Long userId);
    @Query(value="SELECT g.id FROM milestones m JOIN roadmaps r ON r.id=m.roadmap_id JOIN goals g ON g.id=r.goal_id WHERE m.id=:id AND g.user_id=:owner", nativeQuery=true)
    Optional<Long> ownedMilestoneGoal(@Param("id") Long id, @Param("owner") Long owner);
    boolean existsByCategoryId(Long categoryId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Commitment c where c.id=:id and c.userId=:owner")
    Optional<Commitment> lockOwned(@Param("id") Long id, @Param("owner") Long owner);
}
