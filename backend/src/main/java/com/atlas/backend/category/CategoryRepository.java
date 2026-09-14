package com.atlas.backend.category;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByIdAndUserId(Long id, Long userId);
    List<Category> findAllByUserIdOrderByIdAsc(Long userId);
    boolean existsByIdAndUserId(Long id, Long userId);
}
