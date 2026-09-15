package com.atlas.backend.category;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Category c where c.id=:id and c.userId=:owner")
    Optional<Category> lockOwned(@org.springframework.data.repository.query.Param("id") Long id, @org.springframework.data.repository.query.Param("owner") Long owner);
    Optional<Category> findByIdAndUserId(Long id, Long userId);
    List<Category> findAllByUserIdOrderByIdAsc(Long userId);
    boolean existsByIdAndUserId(Long id, Long userId);
}
