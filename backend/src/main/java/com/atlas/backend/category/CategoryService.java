package com.atlas.backend.category;

import com.atlas.backend.recurringintention.RecurringIntentionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns user-scoped Category CRUD and protects references from deletion. */
@Service
public class CategoryService {
    private final com.atlas.backend.commitment.CommitmentRepository commitments;
    private final CategoryRepository categoryRepository;
    private final RecurringIntentionRepository recurringIntentionRepository;

    public CategoryService(CategoryRepository categoryRepository, RecurringIntentionRepository recurringIntentionRepository, com.atlas.backend.commitment.CommitmentRepository commitments) {
        this.commitments = commitments;
        this.categoryRepository = categoryRepository;
        this.recurringIntentionRepository = recurringIntentionRepository;
    }

    @Transactional
    public CategoryResponse create(Long userId, CreateCategoryRequest request) {
        Category category = Category.create(userId, request.name(), request.defaultFlexibilityTier(), request.color());
        category.setDefaultImportance(importance(request.defaultImportance()));
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(Long userId) {
        return categoryRepository.findAllByUserIdOrderByIdAsc(userId).stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(Long userId, Long id) { return CategoryResponse.from(findOwned(userId, id)); }

    @Transactional
    public CategoryResponse update(Long userId, Long id, UpdateCategoryRequest request) {
        Category category = categoryRepository.lockOwned(id, userId).orElseThrow(CategoryNotFoundException::new);
        if (request.defaultImportance() != null) category.setDefaultImportance(importance(request.defaultImportance()));
        category.update(request.name(), request.defaultFlexibilityTier(), request.color());
        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Category category = categoryRepository.lockOwned(id, userId).orElseThrow(CategoryNotFoundException::new);
        if (recurringIntentionRepository.existsByCategoryId(id) || commitments.existsByCategoryId(id)) throw new CategoryInUseException();
        categoryRepository.delete(category);
    }

    private String importance(tools.jackson.databind.JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (!value.isString() || !java.util.Set.of("low", "medium", "high", "critical").contains(value.asString()))
            throw new CategoryImportanceException();
        return value.asString();
    }
    private Category findOwned(Long userId, Long id) {
        return categoryRepository.findByIdAndUserId(id, userId).orElseThrow(CategoryNotFoundException::new);
    }
}
