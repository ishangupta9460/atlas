package com.atlas.backend.dependency;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommitmentDependencyRepository extends JpaRepository<CommitmentDependency, Long> {
    Optional<CommitmentDependency> findByBlockingCommitmentIdAndBlockedCommitmentId(Long blocking, Long blocked);
    List<CommitmentDependency> findByBlockedCommitmentIdOrderByBlockingCommitmentIdAsc(Long blocked);
    @Query("select d from CommitmentDependency d, Commitment c where d.blockingCommitmentId = c.id and c.userId = :owner")
    List<CommitmentDependency> findAllOwned(@Param("owner") Long owner);
    @Query(value = "SELECT id FROM users WHERE id = :owner FOR UPDATE", nativeQuery = true)
    Long lockOwner(@Param("owner") Long owner);
}
