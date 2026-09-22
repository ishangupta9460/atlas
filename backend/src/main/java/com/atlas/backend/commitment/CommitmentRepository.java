package com.atlas.backend.commitment;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CommitmentRepository extends JpaRepository<Commitment, Long> {
    @Query("select c from Commitment c where c.userId=:owner and c.id<:cursor " +
           "and (:excluded is null or c.id<>:excluded) " +
           "and locate(lower(:search), lower(coalesce(c.title, ''))) > 0 order by c.id desc")
    java.util.List<Commitment> searchOwned(@Param("owner") Long owner, @Param("cursor") Long cursor,
            @Param("excluded") Long excluded, @Param("search") String search,
            org.springframework.data.domain.Pageable pageable);
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
