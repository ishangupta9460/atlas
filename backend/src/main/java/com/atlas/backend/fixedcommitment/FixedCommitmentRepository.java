package com.atlas.backend.fixedcommitment;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FixedCommitmentRepository extends JpaRepository<FixedCommitment, Long> {
    Optional<FixedCommitment> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FixedCommitment f where f.id = :id and f.userId = :userId")
    Optional<FixedCommitment> lockOwned(@Param("id") Long id, @Param("userId") Long userId);
}
